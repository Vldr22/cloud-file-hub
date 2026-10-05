package org.resume.s3filemanager.service.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.s3filemanager.constant.ValidationMessages;
import org.resume.s3filemanager.properties.FileUploadProperties;
import org.resume.s3filemanager.validation.FileBatchValidator;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileBatchValidator — проверяет количество файлов в пакетной загрузке")
public class FileBatchValidatorTest {

    private static final int MAX_BATCH_SIZE = 5;

    @Mock
    private ConstraintValidatorContext constraintValidatorContext;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @Mock
    private FileUploadProperties fileUploadProperties;

    @InjectMocks
    private FileBatchValidator fileBatchValidator;

    @Test
    @DisplayName("isValid - отклоняет null вместо массива файлов")
    void isValid_shouldReturnFalse_whenFilesAreNull() {
        // given
        when(constraintValidatorContext.buildConstraintViolationWithTemplate(any()))
                .thenReturn(violationBuilder);

        // when
        boolean result = fileBatchValidator.isValid(null, constraintValidatorContext);

        // then
        assertThat(result).isFalse();
        verify(constraintValidatorContext).disableDefaultConstraintViolation();
        verify(constraintValidatorContext)
                .buildConstraintViolationWithTemplate(ValidationMessages.FILE_EMPTY);
    }

    @Test
    @DisplayName("isValid - отклоняет пустой массив файлов")
    void isValid_shouldReturnFalse_whenFilesArrayIsEmpty() {
        // given
        when(constraintValidatorContext.buildConstraintViolationWithTemplate(any()))
                .thenReturn(violationBuilder);

        // when
        boolean result = fileBatchValidator.isValid(new MultipartFile[0], constraintValidatorContext);

        // then
        assertThat(result).isFalse();
        verify(constraintValidatorContext).disableDefaultConstraintViolation();
        verify(constraintValidatorContext)
                .buildConstraintViolationWithTemplate(ValidationMessages.FILE_EMPTY);

    }

    @Test
    @DisplayName("isValid - отклоняет превышение лимита батча")
    void isValid_shouldReturnFalse_whenBatchSizeExceeded() {
        // given
        when(fileUploadProperties.getMaxBatchSize()).thenReturn(MAX_BATCH_SIZE);
        when(constraintValidatorContext.buildConstraintViolationWithTemplate(any()))
                .thenReturn(violationBuilder);
        MultipartFile[] files = filesOf(MAX_BATCH_SIZE + 1);

        // when
        boolean result = fileBatchValidator.isValid(files, constraintValidatorContext);

        // then
        assertThat(result).isFalse();
        verify(constraintValidatorContext).buildConstraintViolationWithTemplate(
                String.format(ValidationMessages.MAX_FILES_EXCEEDED, MAX_BATCH_SIZE));
    }

    @Test
    @DisplayName("isValid - пропускает количество файлов ровно по лимиту")
    void isValid_shouldReturnTrue_whenBatchSizeIsValid() {

        // given
        when(fileUploadProperties.getMaxBatchSize()).thenReturn(MAX_BATCH_SIZE);
        MultipartFile[] files = filesOf(MAX_BATCH_SIZE);

        // when
        boolean result = fileBatchValidator.isValid(files, constraintValidatorContext);

        // then
        assertThat(result).isTrue();
        verifyNoInteractions(constraintValidatorContext);
    }

    private MultipartFile[] filesOf(int count) {
        MultipartFile[] files = new MultipartFile[count];
        for (int i = 0; i < count; i++) {
            files[i] = new MockMultipartFile("file", new byte[]{1});
        }
        return files;
    }

}
