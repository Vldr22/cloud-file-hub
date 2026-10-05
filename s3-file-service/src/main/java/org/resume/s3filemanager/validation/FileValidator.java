package org.resume.s3filemanager.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.s3filemanager.constant.ValidationMessages;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

/**
 * Валидатор файлов с двухуровневой проверкой типа.
 * <p>
 * Выполняет валидацию на основе:
 * <ul>
 *   <li>Расширения и MIME-типа файла (первый уровень)</li>
 *   <li>Реальной сигнатуры файла через Apache Tika (второй уровень)</li>
 * </ul>
 * Защищает от подмены типа файла путем переименования.
 *
 * @see ValidFile
 * @see TikaFileDetector
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileValidator implements ConstraintValidator<ValidFile, MultipartFile> {

    private final TikaFileDetector tikaFileDetector;

    /**
     * Выполняет Bean Validation проверку файла.
     *
     * @param file файл для валидации
     * @param context контекст валидатора для добавления ошибок
     * @return true если файл валиден, false в противном случае
     */
    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
        Optional<String> error = validateFile(file);
        return error.map(s -> ConstraintValidatorUtils.addViolation(context, s)).orElse(true);
    }

    /**
     * Выполняет программную валидацию файла без Bean Validation контекста.
     * <p>
     * Используется для пакетной загрузки, где требуется обработка частичного успеха.
     * Проверяет:
     * <ul>
     *   <li>Наличие файла и наличие в нем содержания</li>
     *   <li>Корректность имени файла и расширения</li>
     *   <li>Тип файла является разрешенным</li>
     *   <li>Соответствие реальной сигнатуры заявленному типу</li>
     * </ul>
     *
     * @param file файл для валидации
     * @return Optional с сообщением об ошибке, если файл невалиден; пустой Optional, если валиден
     */
    public Optional<String> validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Optional.of(ValidationMessages.FILE_EMPTY);
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            return Optional.of(ValidationMessages.FILE_TYPE_UNKNOWN);
        }

        String extension = StringUtils.getFilenameExtension(filename);
        if (extension == null || extension.isBlank()) {
            return Optional.of(ValidationMessages.FILE_TYPE_UNKNOWN);
        }
        extension = extension.toLowerCase();

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return Optional.of(ValidationMessages.FILE_TYPE_UNKNOWN);
        }

        if (!AllowedFileType.isAllowed(extension, contentType)) {
            return Optional.of(
                    String.format(ValidationMessages.FILE_TYPE_NOT_ALLOWED, extension, contentType)
            );
        }

        try {
            byte[] fileBytes = file.getBytes();
            boolean isValid = tikaFileDetector.verifyContentType(fileBytes, filename, contentType);

            if (!isValid) {
                return Optional.of(
                        String.format(ValidationMessages.FILE_SIGNATURE_MISMATCH, extension)
                );
            }

            return Optional.empty();

        } catch (IOException e) {
            log.error("Error reading file: {}", filename, e);
            return Optional.of(ValidationMessages.FILE_PROCESSING_ERROR);
        } catch (Exception e) {
            log.error("Unexpected error during validation: {}", filename, e);
            return Optional.of(ValidationMessages.FILE_PROCESSING_ERROR);
        }
    }
}
