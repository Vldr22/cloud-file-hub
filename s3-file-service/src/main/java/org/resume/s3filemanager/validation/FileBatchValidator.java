package org.resume.s3filemanager.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.resume.s3filemanager.constant.ValidationMessages;
import org.resume.s3filemanager.properties.FileUploadProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/**
 * Проверяет количество файлов в пакетной загрузке: не пусто и не превышает лимит.
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties({FileUploadProperties.class})
public class FileBatchValidator implements ConstraintValidator<ValidBatchSize, MultipartFile[]> {

    private final FileUploadProperties fileUploadProperties;

    @Override
    public boolean isValid(MultipartFile[] files, ConstraintValidatorContext context) {
        Optional<String> error = validateBatch(files);
        return error.map(s -> ConstraintValidatorUtils.addViolation(context, s)).orElse(true);
    }

    /**
     * Возвращает сообщение об ошибке, если файлов нет или превышен лимит батча.
     */
    private Optional<String> validateBatch(MultipartFile[] files) {

        if (files == null || files.length == 0) {
            return Optional.of(ValidationMessages.FILE_EMPTY);
        }

        if (files.length > fileUploadProperties.getMaxBatchSize()) {
            return Optional.of(String.format(
                    ValidationMessages.MAX_FILES_EXCEEDED,
                    fileUploadProperties.getMaxBatchSize()
            ));
        }

        return Optional.empty();
    }

}
