package org.resume.s3filemanager.service.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.s3filemanager.constant.ValidationMessages;
import org.resume.s3filemanager.validation.FileValidator;
import org.resume.s3filemanager.validation.TikaFileDetector;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileValidator — двухуровневая валидация файлов")
class FileValidatorTest {

    @Mock
    private TikaFileDetector tikaFileDetector;

    @Mock
    private ConstraintValidatorContext constraintValidatorContext;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @InjectMocks
    private FileValidator fileValidator;

    private MockMultipartFile validPdfFile;

    @BeforeEach
    void setUp() {
        validPdfFile = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                new byte[]{1, 2, 3});
    }

    // validateFile — null и пустой файл

    /**
     * null файл — сразу возвращаем FILE_EMPTY, до Tika не доходим.
     */
    @Test
    void shouldReturnFileEmpty_whenFileIsNull() {
        Optional<String> result = fileValidator.validateFile(null);

        assertThat(result).contains(ValidationMessages.FILE_EMPTY);
        verifyNoInteractions(tikaFileDetector);
    }

    /**
     * Файл без содержимого — то же поведение, что и null.
     */
    @Test
    void shouldReturnFileEmpty_whenFileHasNoContent() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);

        Optional<String> result = fileValidator.validateFile(file);

        assertThat(result).contains(ValidationMessages.FILE_EMPTY);
        verifyNoInteractions(tikaFileDetector);
    }

    // validateFile — имя и расширение

    /**
     * Файл без имени — не можем определить тип, возвращаем FILE_TYPE_UNKNOWN.
     */
    @Test
    void shouldReturnTypeUnknown_whenFilenameIsBlank() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "",
                "application/pdf",
                new byte[]{1, 2, 3});

        Optional<String> result = fileValidator.validateFile(file);

        assertThat(result).contains(ValidationMessages.FILE_TYPE_UNKNOWN);
    }

    /**
     * Файл без расширения — не можем проверить допустимость типа.
     */
    @Test
    void shouldReturnTypeUnknown_whenFileHasNoExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "somefile",
                "application/pdf",
                new byte[]{1, 2, 3});

        Optional<String> result = fileValidator.validateFile(file);

        assertThat(result).contains(ValidationMessages.FILE_TYPE_UNKNOWN);
    }

    /**
     * Клиент прислал общий Content-Type (как Swagger UI) - тип определяется по содержимому, файл принимается.
     */
    @Test
    void shouldReturnEmpty_whenClientContentTypeIsGeneric() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/octet-stream",
                new byte[]{1, 2, 3});

        when(tikaFileDetector.detectContentType(any(), eq("document.pdf"))).thenReturn("application/pdf");

        Optional<String> result = fileValidator.validateFile(file);

        assertThat(result).isEmpty();
    }

    // validateFile — недопустимый тип

    /**
     * .exe файл — запрещённый тип, сообщение об ошибке содержит расширение
     */
    @Test
    void shouldReturnTypeNotAllowed_whenFileIsExecutable() {
        var file = new MockMultipartFile(
                "file",
                "example.exe",
                "application/x-msdownload",
                new byte[]{1, 2, 3});

        Optional<String> result = fileValidator.validateFile(file);

        assertThat(result).hasValueSatisfying(msg -> assertThat(msg).contains("exe"));
        verifyNoInteractions(tikaFileDetector);
    }

    /**
     * Файл с null именем — не можем определить тип.
     */
    @Test
    void shouldReturnTypeUnknown_whenFilenameIsNull() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                null,
                "application/pdf",
                new byte[]{1, 2, 3});

        Optional<String> result = fileValidator.validateFile(file);

        assertThat(result).contains(ValidationMessages.FILE_TYPE_UNKNOWN);
        verifyNoInteractions(tikaFileDetector);
    }

    // validateFile — несоответствие сигнатуры

    /**
     * PNG переименован в .pdf — Tika видит реальную сигнатуру и отклоняет файл.
     * Защита от обхода фильтра через переименование
     */
    @Test
    void shouldReturnTypeNotAllowed_whenFileIsRenamedToWrongExtension() {
        MockMultipartFile file = validPdfFile;

        when(tikaFileDetector.detectContentType(any(), eq("document.pdf"))).thenReturn("image/png");

        Optional<String> result = fileValidator.validateFile(file);

        assertThat(result).contains(String.format(ValidationMessages.FILE_TYPE_NOT_ALLOWED, "pdf", "image/png"));
    }


    // validateFile — успешная валидация

    /**
     * Валидный PDF — расширение и тип по содержимому совпадают, ошибок нет
     */
    @Test
    void shouldReturnEmpty_whenFilePassesAllChecks() {
        MockMultipartFile file = validPdfFile;

        when(tikaFileDetector.detectContentType(any(), eq("document.pdf"))).thenReturn("application/pdf");

        Optional<String> result = fileValidator.validateFile(file);

        assertThat(result).isEmpty();

        verify(tikaFileDetector).detectContentType(any(), eq("document.pdf"));
    }

    // validateFile — обработка исключений

    /**
     * IOException при чтении байтов — возвращаем FILE_PROCESSING_ERROR, не пробрасываем.
     * MockMultipartFile мокируется вручную — единственный способ заставить getBytes() бросить IOException
     */
    @Test
    void shouldReturnProcessingError_whenReadingFileBytesThrowsIOException() throws Exception {
        MockMultipartFile file = mock(MockMultipartFile.class);

        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("document.pdf");
        when(file.getBytes()).thenThrow(new IOException());

        Optional<String> result = fileValidator.validateFile(file);

        assertThat(result).contains(ValidationMessages.FILE_PROCESSING_ERROR);
    }

    /**
     * Неожиданное исключение от TikaFileDetector — не пробрасываем наружу,
     * возвращаем FILE_PROCESSING_ERROR.
     */
    @Test
    void shouldReturnProcessingError_whenTikaThrowsUnexpectedException() {
        MockMultipartFile file = validPdfFile;

        when(tikaFileDetector.detectContentType(any(), any()))
                .thenThrow(new RuntimeException());

        Optional<String> result = fileValidator.validateFile(file);

        assertThat(result).contains(ValidationMessages.FILE_PROCESSING_ERROR);
    }

    // isValid — Bean Validation интеграция

    /**
     * Невалидный файл — isValid возвращает false и регистрирует нарушение в контексте.
     */
    @Test
    void shouldReturnFalse_andRegisterViolation_whenFileIsInvalid() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);

        when(constraintValidatorContext.buildConstraintViolationWithTemplate(any()))
                .thenReturn(violationBuilder);

        boolean result = fileValidator.isValid(file, constraintValidatorContext);

        assertThat(result).isFalse();
        verify(constraintValidatorContext).disableDefaultConstraintViolation();
        verify(constraintValidatorContext).buildConstraintViolationWithTemplate(any());
    }

    /**
     * Валидный файл — isValid возвращает true, контекст не трогается.
     */
    @Test
    void shouldReturnTrue_andNotTouchContext_whenFileIsValid() {
        MockMultipartFile file = validPdfFile;

        when(tikaFileDetector.detectContentType(any(), eq("document.pdf"))).thenReturn("application/pdf");

        boolean result = fileValidator.isValid(file, constraintValidatorContext);

        assertThat(result).isTrue();
        verifyNoInteractions(constraintValidatorContext);
    }
}