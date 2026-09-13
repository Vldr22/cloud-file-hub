package org.resume.s3filemanager.service.file;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.s3filemanager.constant.ErrorMessages;
import org.resume.s3filemanager.constant.ValidationMessages;
import org.resume.s3filemanager.dto.FileDownloadResponse;
import org.resume.s3filemanager.dto.MultipleUploadResponse;
import org.resume.s3filemanager.entity.FileMetadata;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.CommonResponseStatus;
import org.resume.s3filemanager.exception.*;
import org.resume.s3filemanager.service.file.strategy.DuplicateFileErrorStrategy;
import org.resume.s3filemanager.service.file.strategy.ErrorResponseStrategyResolver;
import org.resume.s3filemanager.service.file.strategy.FileReadErrorStrategy;
import org.resume.s3filemanager.service.file.strategy.S3YandexErrorStrategy;
import org.resume.s3filemanager.service.kafka.OutboxService;
import org.resume.s3filemanager.validation.FileValidator;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileFacadeService — координация загрузки, скачивания и удаления файлов")
class FileFacadeServiceTest {

    private static final Faker FAKER = new Faker();

    @Mock
    private FileHashService fileHashService;

    @Mock
    private YandexStorageService fileStorageService;

    @Mock
    private FileMetadataService fileMetadataService;

    @Mock
    private FilePermissionService filePermissionService;

    @Mock
    private FileValidator fileValidator;

    @Mock
    private OutboxService outboxService;

    @Spy
    private ErrorResponseStrategyResolver errorResponseStrategyResolver =
            new ErrorResponseStrategyResolver(List.of(
                    new DuplicateFileErrorStrategy(),
                    new FileReadErrorStrategy(),
                    new S3YandexErrorStrategy()));

    @InjectMocks
    private FileFacadeService fileFacadeService;

    private User user;
    private MockMultipartFile validPdfFile;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(FAKER.number().randomNumber());
        user.setUsername(FAKER.name().username());

        validPdfFile = new MockMultipartFile(
                "file",
                FAKER.file().fileName(null, null, "pdf", null),
                "application/pdf",
                FAKER.lorem().characters(10).getBytes());
    }

    // uploadFile

    /**
     * Успешная загрузка — файл уходит в S3, метаданные сохраняются, событие публикуется.
     */
    @Test
    void shouldUploadFile_whenAllChecksPass() {
        String fileHash = FAKER.internet().uuid();
        FileMetadata saved = buildFileMetadata();

        when(filePermissionService.checkUploadPermission()).thenReturn(user);
        when(fileHashService.calculateMD5(any())).thenReturn(fileHash);
        when(fileMetadataService.saveFileWithPermission(any(), anyString(), eq(fileHash), eq(user)))
                .thenReturn(saved);

        fileFacadeService.uploadFile(validPdfFile);

        verify(fileStorageService).uploadFileYandexS3(anyString(), any(), eq("application/pdf"));
        verify(fileMetadataService).saveFileWithPermission(any(), anyString(), eq(fileHash), eq(user));
        verify(outboxService).saveFileUploadEvent(eq(saved), eq(user.getId()));
    }

    /**
     * Нет прав на загрузку — FileUploadLimitException пробрасывается сразу, до S3 не доходим.
     */
    @Test
    void shouldThrowFileUploadLimitException_whenUserHasNoUploadPermission() {
        when(filePermissionService.checkUploadPermission())
                .thenThrow(new FileUploadLimitException());

        assertThatThrownBy(() -> fileFacadeService.uploadFile(validPdfFile))
                .isInstanceOf(FileUploadLimitException.class);

        verifyNoInteractions(fileStorageService, fileMetadataService, outboxService);
    }

    /**
     * Дубликат файла — DuplicateFileException пробрасывается до загрузки в S3.
     */
    @Test
    void shouldThrowDuplicateFileException_whenFileHashAlreadyExists() {
        when(filePermissionService.checkUploadPermission()).thenReturn(user);
        when(fileHashService.calculateMD5(any())).thenReturn(FAKER.internet().uuid());
        doThrow(new DuplicateFileException())
                .when(fileHashService).checkDuplicateInDatabase(anyString(), any());

        assertThatThrownBy(() -> fileFacadeService.uploadFile(validPdfFile))
                .isInstanceOf(DuplicateFileException.class);

        verifyNoInteractions(fileStorageService, fileMetadataService, outboxService);
    }

    /**
     * Ошибка сохранения в БД — файл откатывается из S3 (паттерн Saga).
     */
    @Test
    void shouldRollbackS3Upload_whenDatabaseSaveFails() {
        when(filePermissionService.checkUploadPermission()).thenReturn(user);
        when(fileHashService.calculateMD5(any())).thenReturn(FAKER.internet().uuid());
        when(fileMetadataService.saveFileWithPermission(any(), anyString(), anyString(), eq(user)))
                .thenThrow(new RuntimeException());

        assertThatThrownBy(() -> fileFacadeService.uploadFile(validPdfFile))
                .isInstanceOf(RuntimeException.class);

        verify(fileStorageService).uploadFileYandexS3(anyString(), any(), anyString());
        verify(fileStorageService).deleteFileYandexS3(anyString());
        verifyNoInteractions(outboxService);
    }

    // multipleUpload

    /**
     * Пакетная загрузка — один файл валидный, один невалидный.
     * Валидный загружается, невалидный отклоняется с сообщением из ValidationMessages.
     */
    @Test
    void shouldReturnPartialSuccess_whenOneFileIsInvalidAndOneIsValid() {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                FAKER.file().fileName(null, null, "exe", null),
                "application/x-msdownload",
                FAKER.lorem().characters(5).getBytes())
                ;
        String validationError = String.format(
                ValidationMessages.FILE_TYPE_NOT_ALLOWED, "exe", "application/x-msdownload");

        FileMetadata saved = buildFileMetadata();

        when(filePermissionService.checkUploadPermission()).thenReturn(user);
        when(fileValidator.validateFile(validPdfFile)).thenReturn(Optional.empty());
        when(fileValidator.validateFile(invalidFile)).thenReturn(Optional.of(validationError));
        when(fileHashService.calculateMD5(any())).thenReturn(FAKER.internet().uuid());
        when(fileMetadataService.saveFileWithPermission(any(), anyString(), anyString(), eq(user)))
                .thenReturn(saved);

        List<MultipleUploadResponse> results = fileFacadeService.multipleUpload(
                new MultipartFile[]{validPdfFile, invalidFile});

        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo(CommonResponseStatus.SUCCESS);
        assertThat(results.get(1).status()).isEqualTo(CommonResponseStatus.ERROR);
        assertThat(results.get(1).message()).isEqualTo(validationError);

        verify(fileStorageService).uploadFileYandexS3(anyString(), any(), anyString());
    }

    /**
     * Все файлы провалились — бросаем MultipleFileUploadException со списком результатов.
     */
    @Test
    void shouldThrowMultipleFileUploadException_whenAllFilesFail() {
        String validationError = String.format(ValidationMessages.FILE_TYPE_NOT_ALLOWED, "exe", "application/x-msdownload");
        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "a.exe",
                "application/x-msdownload",
                new byte[]{1});

        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "b.exe",
                "application/x-msdownload",
                new byte[]{1});

        when(filePermissionService.checkUploadPermission()).thenReturn(user);
        when(fileValidator.validateFile(any())).thenReturn(Optional.of(validationError));

        assertThatThrownBy(() -> fileFacadeService.multipleUpload(new MultipartFile[]{file1, file2}))
                .isInstanceOf(MultipleFileUploadException.class)
                .satisfies(ex -> {
                    MultipleFileUploadException mex = (MultipleFileUploadException) ex;
                    assertThat(mex.getResponses()).hasSize(2);
                    assertThat(mex.getResponses()).allMatch(r -> r.status() == CommonResponseStatus.ERROR);
                });

        verifyNoInteractions(fileStorageService);
    }

    /**
     * Дубликат в пакетной загрузке — дубликат получает ERROR, второй файл загружается успешно.
     */
    @Test
    void shouldMarkFileAsError_whenDuplicateDetectedDuringBatchUpload() {
        MockMultipartFile duplicate = new MockMultipartFile(
                "file",
                FAKER.file().fileName(null, null, "pdf", null),
                "application/pdf",
                FAKER.lorem().characters(5).getBytes());

        FileMetadata saved = buildFileMetadata();

        when(filePermissionService.checkUploadPermission()).thenReturn(user);
        when(fileValidator.validateFile(any())).thenReturn(Optional.empty());
        when(fileHashService.calculateMD5(any())).thenReturn(FAKER.internet().uuid());
        doThrow(new DuplicateFileException())
                .doNothing()
                .when(fileHashService).checkDuplicateInDatabase(anyString(), any());
        when(fileMetadataService.saveFileWithPermission(any(), anyString(), anyString(), eq(user)))
                .thenReturn(saved);

        List<MultipleUploadResponse> results = fileFacadeService.multipleUpload(
                new MultipartFile[]{duplicate, validPdfFile});

        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo(CommonResponseStatus.ERROR);
        assertThat(results.get(0).message()).isEqualTo(ErrorMessages.FILE_ALREADY_BEEN_UPLOADED);
        assertThat(results.get(1).status()).isEqualTo(CommonResponseStatus.SUCCESS);
    }

    /**
     * S3 ошибка при загрузке в пакетном режиме — файл получает FILE_STORAGE_ERROR.
     */
    @Test
    void shouldMarkFileAsError_whenS3FailsDuringBatchUpload() {
        MockMultipartFile secondFile = new MockMultipartFile(
                "file",
                FAKER.file().fileName(null, null, "pdf", null),
                "application/pdf",
                FAKER.lorem().characters(10).getBytes());

        when(filePermissionService.checkUploadPermission()).thenReturn(user);
        when(fileValidator.validateFile(any())).thenReturn(Optional.empty());
        when(fileHashService.calculateMD5(any())).thenReturn(FAKER.internet().uuid());

        doThrow(new S3YandexException(new RuntimeException(), FAKER.internet().uuid()))
                .doNothing()
                .when(fileStorageService).uploadFileYandexS3(anyString(), any(), anyString());
        when(fileMetadataService.saveFileWithPermission(any(), anyString(), anyString(), eq(user)))
                .thenReturn(buildFileMetadata());

        List<MultipleUploadResponse> results = fileFacadeService.multipleUpload(
                new MultipartFile[]{validPdfFile, secondFile});

        assertThat(results.get(0).status()).isEqualTo(CommonResponseStatus.ERROR);
        assertThat(results.get(0).message()).isEqualTo(ErrorMessages.FILE_STORAGE_ERROR);
        assertThat(results.get(1).status()).isEqualTo(CommonResponseStatus.SUCCESS);
    }

    /**
     * Ошибка чтения файла в пакетном режиме — FILE_READ_ERROR.
     */
    @Test
    void shouldMarkFileAsError_whenFileReadFailsDuringBatchUpload() throws Exception {
        MockMultipartFile unreadableFile = mock(MockMultipartFile.class);
        when(unreadableFile.getOriginalFilename()).thenReturn("broken.pdf");
        when(unreadableFile.getBytes()).thenThrow(new java.io.IOException());

        when(filePermissionService.checkUploadPermission()).thenReturn(user);
        when(fileHashService.calculateMD5(any())).thenReturn(FAKER.internet().uuid());
        when(fileMetadataService.saveFileWithPermission(any(), anyString(), anyString(), eq(user)))
                .thenReturn(buildFileMetadata());

        List<MultipleUploadResponse> results = fileFacadeService.multipleUpload(
                new MultipartFile[]{unreadableFile, validPdfFile});

        assertThat(results.get(0).status()).isEqualTo(CommonResponseStatus.ERROR);
        assertThat(results.get(0).message()).isEqualTo(ErrorMessages.FILE_READ_ERROR);
        assertThat(results.get(1).status()).isEqualTo(CommonResponseStatus.SUCCESS);
    }


    // downloadFile

    /**
     * Успешное скачивание — возвращаем байты, кириллическое имя кодируется для заголовка.
     */
    @Test
    void shouldReturnFileContent_whenFileExistsInStorage() {
        byte[] content = FAKER.lorem().characters(20).getBytes();
        String uniqueName = FAKER.internet().uuid() + ".pdf";
        FileMetadata metadata = buildFileMetadataWithName("example.pdf");

        when(fileStorageService.downloadFileYandexS3(uniqueName)).thenReturn(content);
        when(fileMetadataService.findByUniqueName(uniqueName)).thenReturn(metadata);

        FileDownloadResponse response = fileFacadeService.downloadFile(uniqueName);

        assertThat(response.getContent()).isEqualTo(content);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getSize()).isEqualTo(content.length);
        assertThat(response.getFileName()).doesNotContain(" ");

        verify(fileStorageService).downloadFileYandexS3(uniqueName);
        verify(fileMetadataService).findByUniqueName(uniqueName);
    }

    /**
     * Файл не найден в БД — FileNotFoundException пробрасывается.
     */
    @Test
    void shouldThrowFileNotFoundException_whenMetadataNotFound() {
        String uniqueName = FAKER.internet().uuid() + ".pdf";

        when(fileMetadataService.findByUniqueName(uniqueName))
                .thenThrow(new FileNotFoundException(uniqueName));

        assertThatThrownBy(() -> fileFacadeService.downloadFile(uniqueName))
                .isInstanceOf(FileNotFoundException.class);
    }

    /**
     * Файл есть в БД, но отсутствует в S3 — FileNotFoundException пробрасывается.
     */
    @Test
    void shouldThrowFileNotFoundException_whenFileNotInS3() {
        String uniqueName = FAKER.internet().uuid() + ".pdf";

        when(fileMetadataService.findByUniqueName(uniqueName))
                .thenReturn(mock(FileMetadata.class));
        when(fileStorageService.downloadFileYandexS3(uniqueName))
                .thenThrow(new FileNotFoundException(uniqueName));

        assertThatThrownBy(() -> fileFacadeService.downloadFile(uniqueName))
                .isInstanceOf(FileNotFoundException.class);
    }

    // deleteFile

    /**
     * Успешное удаление — файл удаляется из S3 и БД.
     */
    @Test
    void shouldDeleteFile_whenUserIsOwner() {
        FileMetadata file = buildFileMetadata();
        String uniqueName = file.getUniqueName();

        when(filePermissionService.getCurrentUser()).thenReturn(user);
        when(fileMetadataService.findByUniqueName(uniqueName)).thenReturn(file);

        fileFacadeService.deleteFile(uniqueName);

        verify(fileStorageService).deleteFileYandexS3(uniqueName);
        verify(fileMetadataService).deleteFileAndUpdateUserStatus(file);
    }

    /**
     * Нет прав на удаление чужого файла — FileAccessDeniedException, файл не трогаем.
     */
    @Test
    void shouldThrowFileAccessDeniedException_whenUserIsNotOwner() {
        FileMetadata file = buildFileMetadata();
        String uniqueName = file.getUniqueName();

        when(filePermissionService.getCurrentUser()).thenReturn(user);
        when(fileMetadataService.findByUniqueName(uniqueName)).thenReturn(file);
        doThrow(new FileAccessDeniedException())
                .when(filePermissionService).checkDeletePermission(user, file);

        assertThatThrownBy(() -> fileFacadeService.deleteFile(uniqueName))
                .isInstanceOf(FileAccessDeniedException.class);

        verifyNoInteractions(fileStorageService);
        verify(fileMetadataService, never()).deleteFileAndUpdateUserStatus(any());
    }

    // helpers
    private FileMetadata buildFileMetadata() {
        return buildFileMetadataWithName(FAKER.file().fileName(
                null,
                null,
                "pdf",
                null));
    }

    private FileMetadata buildFileMetadataWithName(String originalName) {
        return FileMetadata.builder()
                .uniqueName(FAKER.internet().uuid() + ".pdf")
                .originalName(originalName)
                .type("application/pdf")
                .size(FAKER.number().numberBetween(1L, 10_000_000L))
                .fileHash(FAKER.internet().uuid())
                .user(user)
                .build();
    }
}