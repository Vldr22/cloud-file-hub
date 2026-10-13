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
 *   <li>Расширения файла по whitelist (первый уровень)</li>
 *   <li>Типа, определённого Apache Tika по содержимому, для этого расширения (второй уровень)</li>
 * </ul>
 * Заявленный клиентом Content-Type не учитывается. Защищает от подмены типа файла путем переименования.
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
     *   <li>Расширение файла является разрешенным</li>
     *   <li>Тип по содержимому соответствует расширению</li>
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

        if (!AllowedFileType.isExtensionAllowed(extension)) {
            return Optional.of(String.format(ValidationMessages.FILE_EXTENSION_NOT_ALLOWED, extension));
        }

        try {
            byte[] fileBytes = file.getBytes();
            String detectedType = tikaFileDetector.detectContentType(fileBytes, filename);

            if (!AllowedFileType.isAllowed(extension, detectedType)) {
                return Optional.of(
                        String.format(ValidationMessages.FILE_TYPE_NOT_ALLOWED, extension, detectedType)
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
