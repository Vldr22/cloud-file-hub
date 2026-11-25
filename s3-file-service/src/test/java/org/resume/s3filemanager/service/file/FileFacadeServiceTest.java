package org.resume.s3filemanager.service.file;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.common.model.ScanStatus;
import org.resume.s3filemanager.constant.ErrorMessages;
import org.resume.s3filemanager.constant.ValidationMessages;
import org.resume.s3filemanager.dto.FileDownloadResponse;
import org.resume.s3filemanager.dto.FileResponse;
import org.resume.s3filemanager.dto.MultipleUploadResponse;
import org.resume.s3filemanager.entity.FileMetadata;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.CommonResponseStatus;
import org.resume.s3filemanager.enums.UserRole;
import org.resume.s3filemanager.enums.UserStatus;
import org.resume.s3filemanager.exception.*;
import org.resume.s3filemanager.mapper.FileMapper;
import org.resume.s3filemanager.mapper.FileMapperImpl;
import org.resume.s3filemanager.security.AuthenticatedUser;
import org.resume.s3filemanager.service.file.strategy.DuplicateFileErrorStrategy;
import org.resume.s3filemanager.service.file.strategy.ErrorResponseStrategyResolver;
import org.resume.s3filemanager.service.file.strategy.FileReadErrorStrategy;
import org.resume.s3filemanager.service.file.strategy.S3YandexErrorStrategy;
import org.resume.s3filemanager.validation.FileValidator;
import org.resume.s3filemanager.validation.TikaFileDetector;
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
@DisplayName("FileFacadeService - координация загрузки, скачивания и удаления файлов")
class FileFacadeServiceTest {

    private static final Faker FAKER = new Faker();
    private static final String PDF_TYPE = "application/pdf";

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
    private TikaFileDetector tikaFileDetector;

    @Spy
    private ErrorResponseStrategyResolver errorResponseStrategyResolver =
            new ErrorResponseStrategyResolver(List.of(
                    new DuplicateFileErrorStrategy(),
                    new FileReadErrorStrategy(),
                    new S3YandexErrorStrategy()));

    @Spy
    private S3KeyGenerator s3KeyGenerator = new S3KeyGenerator();

    @Spy
    private FileMapper fileMapper = new FileMapperImpl();

    @InjectMocks
    private FileFacadeService fileFacadeService;

    private User user;
    private AuthenticatedUser authenticatedUser;
    private MockMultipartFile validPdfFile;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(FAKER.number().randomNumber());
        user.setUsername(FAKER.name().username());
        authenticatedUser = new AuthenticatedUser(user.getUsername(), UserRole.USER);

        validPdfFile = new MockMultipartFile(
                "file",
                FAKER.file().fileName(null, null, "pdf", null),
                "application/pdf",
                FAKER.lorem().characters(10).getBytes());

        lenient().when(tikaFileDetector.detectContentType(any(), any())).thenReturn(PDF_TYPE);
    }

    // uploadFile

    @Test
    @DisplayName("Загружает файл в S3 и возвращает ответ со статусом PENDING_SCAN")
    void shouldUploadFile_whenAllChecksPass() {
        //given
        String fileHash = FAKER.internet().uuid();
        FileMetadata saved = buildFileMetadata();

        when(filePermissionService.checkUploadPermission(authenticatedUser)).thenReturn(user);
        when(fileHashService.calculateMD5(any())).thenReturn(fileHash);
        when(fileMetadataService.saveFileWithOutboxEvent(any(), anyString(), eq(fileHash), eq(PDF_TYPE), eq(user)))
                .thenReturn(saved);

        //when
        FileResponse response = fileFacadeService.uploadFile(validPdfFile, authenticatedUser);

        //then
        assertThat(response.uniqueName()).isEqualTo(saved.getUniqueName());
        assertThat(response.scanStatus()).isEqualTo(ScanStatus.PENDING_SCAN);
        verify(fileStorageService).uploadFileYandexS3(anyString(), any(), eq(PDF_TYPE));
        verify(fileMetadataService).saveFileWithOutboxEvent(any(), anyString(), eq(fileHash), eq(PDF_TYPE), eq(user));
    }

    @Test
    @DisplayName("Бросает FileUploadLimitException до обращения к S3, когда нет прав на загрузку")
    void shouldThrowFileUploadLimitException_whenUserHasNoUploadPermission() {
        //given
        when(filePermissionService.checkUploadPermission(authenticatedUser))
                .thenThrow(new FileUploadLimitException());

        //when //then
        assertThatThrownBy(() -> fileFacadeService.uploadFile(validPdfFile, authenticatedUser))
                .isInstanceOf(FileUploadLimitException.class);

        verifyNoInteractions(fileStorageService, fileMetadataService);
    }

    @Test
    @DisplayName("Бросает DuplicateFileException до загрузки в S3 при дубликате файла")
    void shouldThrowDuplicateFileException_whenFileHashAlreadyExists() {
        //given
        when(filePermissionService.checkUploadPermission(authenticatedUser)).thenReturn(user);
        when(fileHashService.calculateMD5(any())).thenReturn(FAKER.internet().uuid());
        doThrow(new DuplicateFileException())
                .when(fileHashService).checkDuplicateInDatabase(anyString(), any());

        //when //then
        assertThatThrownBy(() -> fileFacadeService.uploadFile(validPdfFile, authenticatedUser))
                .isInstanceOf(DuplicateFileException.class);

        verifyNoInteractions(fileStorageService, fileMetadataService);
    }

    @Test
    @DisplayName("Удаляет файл из S3 при ошибке сохранения в БД")
    void shouldRollbackS3Upload_whenDatabaseSaveFails() {
        //given
        when(filePermissionService.checkUploadPermission(authenticatedUser)).thenReturn(user);
        when(fileHashService.calculateMD5(any())).thenReturn(FAKER.internet().uuid());
        when(fileMetadataService.saveFileWithOutboxEvent(any(), anyString(), anyString(), anyString(), eq(user)))
                .thenThrow(new RuntimeException());

        //when //then
        assertThatThrownBy(() -> fileFacadeService.uploadFile(validPdfFile, authenticatedUser))
                .isInstanceOf(RuntimeException.class);

        verify(fileStorageService).uploadFileYandexS3(anyString(), any(), anyString());
        verify(fileStorageService).deleteFileYandexS3(anyString());
    }

    // multipleUpload

    @Test
    @DisplayName("Загружает валидный файл и отклоняет невалидный с сообщением валидации")
    void shouldReturnPartialSuccess_whenOneFileIsInvalidAndOneIsValid() {
        //given
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file",
                FAKER.file().fileName(null, null, "exe", null),
                "application/x-msdownload",
                FAKER.lorem().characters(5).getBytes());

        String validationError = String.format(
                ValidationMessages.FILE_TYPE_NOT_ALLOWED, "exe", "application/x-msdownload");

        FileMetadata saved = buildFileMetadata();

        when(filePermissionService.checkUploadPermission(authenticatedUser)).thenReturn(user);
        when(fileValidator.validateFile(validPdfFile)).thenReturn(Optional.empty());
        when(fileValidator.validateFile(invalidFile)).thenReturn(Optional.of(validationError));
        when(fileHashService.calculateMD5(any())).thenReturn(FAKER.internet().uuid());
        when(fileMetadataService.saveFileWithOutboxEvent(any(), anyString(), anyString(), anyString(), eq(user)))
                .thenReturn(saved);

        //when
        List<MultipleUploadResponse> results = fileFacadeService.multipleUpload(
                new MultipartFile[]{validPdfFile, invalidFile}, authenticatedUser);

        //then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo(CommonResponseStatus.SUCCESS);
        assertThat(results.get(1).status()).isEqualTo(CommonResponseStatus.ERROR);
        assertThat(results.get(1).message()).isEqualTo(validationError);

        verify(fileStorageService).uploadFileYandexS3(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("Бросает MultipleFileUploadException со списком результатов, когда все файлы провалились")
    void shouldThrowMultipleFileUploadException_whenAllFilesFail() {
        //given
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

        when(filePermissionService.checkUploadPermission(authenticatedUser)).thenReturn(user);
        when(fileValidator.validateFile(any())).thenReturn(Optional.of(validationError));

        //when //then
        assertThatThrownBy(() -> fileFacadeService.multipleUpload(new MultipartFile[]{file1, file2}, authenticatedUser))
                .isInstanceOf(MultipleFileUploadException.class)
                .satisfies(ex -> {
                    MultipleFileUploadException mex = (MultipleFileUploadException) ex;
                    assertThat(mex.getResponses()).hasSize(2);
                    assertThat(mex.getResponses()).allMatch(r -> r.status() == CommonResponseStatus.ERROR);
                });

        verifyNoInteractions(fileStorageService);
    }

    @Test
    @DisplayName("Отмечает дубликат ошибкой в пакетной загрузке, второй файл загружается")
    void shouldMarkFileAsError_whenDuplicateDetectedDuringBatchUpload() {
        //given
        MockMultipartFile duplicate = new MockMultipartFile(
                "file",
                FAKER.file().fileName(null, null, "pdf", null),
                "application/pdf",
                FAKER.lorem().characters(5).getBytes());

        FileMetadata saved = buildFileMetadata();

        when(filePermissionService.checkUploadPermission(authenticatedUser)).thenReturn(user);
        when(fileValidator.validateFile(any())).thenReturn(Optional.empty());
        when(fileHashService.calculateMD5(any())).thenReturn(FAKER.internet().uuid());
        doThrow(new DuplicateFileException())
                .doNothing()
                .when(fileHashService).checkDuplicateInDatabase(anyString(), any());
        when(fileMetadataService.saveFileWithOutboxEvent(any(), anyString(), anyString(), anyString(), eq(user)))
                .thenReturn(saved);

        //when
        List<MultipleUploadResponse> results = fileFacadeService.multipleUpload(
                new MultipartFile[]{duplicate, validPdfFile}, authenticatedUser);

        //then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo(CommonResponseStatus.ERROR);
        assertThat(results.get(0).message()).isEqualTo(ErrorMessages.FILE_ALREADY_BEEN_UPLOADED);
        assertThat(results.get(1).status()).isEqualTo(CommonResponseStatus.SUCCESS);
    }

    @Test
    @DisplayName("Отмечает файл ошибкой FILE_STORAGE_ERROR при сбое S3 в пакетной загрузке")
    void shouldMarkFileAsError_whenS3FailsDuringBatchUpload() {
        //given
        MockMultipartFile secondFile = new MockMultipartFile(
                "file",
                FAKER.file().fileName(null, null, "pdf", null),
                "application/pdf",
                FAKER.lorem().characters(10).getBytes());

        when(filePermissionService.checkUploadPermission(authenticatedUser)).thenReturn(user);
        when(fileValidator.validateFile(any())).thenReturn(Optional.empty());
        when(fileHashService.calculateMD5(any())).thenReturn(FAKER.internet().uuid());

        doThrow(new S3YandexException(new RuntimeException(), FAKER.internet().uuid()))
                .doNothing()
                .when(fileStorageService).uploadFileYandexS3(anyString(), any(), anyString());
        when(fileMetadataService.saveFileWithOutboxEvent(any(), anyString(), anyString(), anyString(), eq(user)))
                .thenReturn(buildFileMetadata());

        //when
        List<MultipleUploadResponse> results = fileFacadeService.multipleUpload(
                new MultipartFile[]{validPdfFile, secondFile}, authenticatedUser);

        //then
        assertThat(results.get(0).status()).isEqualTo(CommonResponseStatus.ERROR);
        assertThat(results.get(0).message()).isEqualTo(ErrorMessages.FILE_STORAGE_ERROR);
        assertThat(results.get(1).status()).isEqualTo(CommonResponseStatus.SUCCESS);
    }

    @Test
    @DisplayName("Отмечает файл ошибкой FILE_READ_ERROR при сбое чтения в пакетной загрузке")
    void shouldMarkFileAsError_whenFileReadFailsDuringBatchUpload() throws Exception {
        //given
        MockMultipartFile unreadableFile = mock(MockMultipartFile.class);
        when(unreadableFile.getOriginalFilename()).thenReturn("broken.pdf");
        when(unreadableFile.getBytes()).thenThrow(new java.io.IOException());

        when(filePermissionService.checkUploadPermission(authenticatedUser)).thenReturn(user);
        when(fileHashService.calculateMD5(any())).thenReturn(FAKER.internet().uuid());
        when(fileMetadataService.saveFileWithOutboxEvent(any(), anyString(), anyString(), anyString(), eq(user)))
                .thenReturn(buildFileMetadata());

        //when
        List<MultipleUploadResponse> results = fileFacadeService.multipleUpload(
                new MultipartFile[]{unreadableFile, validPdfFile}, authenticatedUser);

        //then
        assertThat(results.get(0).status()).isEqualTo(CommonResponseStatus.ERROR);
        assertThat(results.get(0).message()).isEqualTo(ErrorMessages.FILE_READ_ERROR);
        assertThat(results.get(1).status()).isEqualTo(CommonResponseStatus.SUCCESS);
    }

    // downloadFile

    @Test
    @DisplayName("Возвращает байты файла и закодированное имя при успешном скачивании")
    void shouldReturnFileContent_whenFileExistsInStorage() {
        //given
        byte[] content = FAKER.lorem().characters(20).getBytes();
        String uniqueName = FAKER.internet().uuid() + ".pdf";
        FileMetadata metadata = buildFileMetadataWithName("example.pdf");
        metadata.setScanStatus(ScanStatus.CLEAN);

        when(fileStorageService.downloadFileYandexS3(uniqueName)).thenReturn(content);
        when(fileMetadataService.findByUniqueName(uniqueName)).thenReturn(metadata);

        //when
        FileDownloadResponse response = fileFacadeService.downloadFile(uniqueName);

        //then
        assertThat(response.getContent()).isEqualTo(content);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getSize()).isEqualTo(content.length);
        assertThat(response.getFileName()).doesNotContain(" ");

        verify(fileStorageService).downloadFileYandexS3(uniqueName);
        verify(fileMetadataService).findByUniqueName(uniqueName);
    }

    @Test
    @DisplayName("Бросает FileNotFoundException, когда файла нет в БД")
    void shouldThrowFileNotFoundException_whenMetadataNotFound() {
        //given
        String uniqueName = FAKER.internet().uuid() + ".pdf";

        when(fileMetadataService.findByUniqueName(uniqueName))
                .thenThrow(new FileNotFoundException(uniqueName));

        //when //then
        assertThatThrownBy(() -> fileFacadeService.downloadFile(uniqueName))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("Бросает FileNotFoundException, когда файл есть в БД, но отсутствует в S3")
    void shouldThrowFileNotFoundException_whenFileNotInS3() {
        //given
        String uniqueName = FAKER.internet().uuid() + ".pdf";
        FileMetadata metadata = buildFileMetadataWithName("example.pdf");
        metadata.setScanStatus(ScanStatus.CLEAN);

        when(fileMetadataService.findByUniqueName(uniqueName)).thenReturn(metadata);
        when(fileStorageService.downloadFileYandexS3(uniqueName))
                .thenThrow(new FileNotFoundException(uniqueName));

        //when //then
        assertThatThrownBy(() -> fileFacadeService.downloadFile(uniqueName))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("Бросает FileNotAvailableException и не обращается к S3, когда файл не CLEAN")
    void shouldThrowFileNotAvailableException_whenFileIsNotClean() {
        //given
        String uniqueName = FAKER.internet().uuid() + ".pdf";
        FileMetadata metadata = buildFileMetadataWithName("example.pdf");

        when(fileMetadataService.findByUniqueName(uniqueName)).thenReturn(metadata);

        //when //then
        assertThatThrownBy(() -> fileFacadeService.downloadFile(uniqueName))
                .isInstanceOf(FileNotAvailableException.class);

        verifyNoInteractions(fileStorageService);
    }

    @Test
    @DisplayName("Бросает FileNotFoundException и не обращается к S3, когда владелец заблокирован")
    void shouldThrowFileNotFoundException_whenOwnerIsBlocked() {
        //given
        String uniqueName = FAKER.internet().uuid() + ".pdf";
        FileMetadata metadata = buildFileMetadataWithName("example.pdf");
        metadata.setScanStatus(ScanStatus.CLEAN);

        User blockedOwner = new User();
        blockedOwner.setId(FAKER.number().randomNumber());
        blockedOwner.setUsername(FAKER.name().username());
        blockedOwner.setStatus(UserStatus.BLOCKED);
        metadata.setUser(blockedOwner);

        when(fileMetadataService.findByUniqueName(uniqueName)).thenReturn(metadata);

        //when //then
        assertThatThrownBy(() -> fileFacadeService.downloadFile(uniqueName))
                .isInstanceOf(FileNotFoundException.class);

        verifyNoInteractions(fileStorageService);
    }

    // deleteFile

    @Test
    @DisplayName("Удаляет сначала метаданные из БД, затем объект из S3")
    void shouldDeleteFile_whenUserIsOwner() {
        //given
        FileMetadata file = buildFileMetadata();
        String uniqueName = file.getUniqueName();

        when(fileMetadataService.findByUniqueName(uniqueName)).thenReturn(file);

        //when
        fileFacadeService.deleteFile(uniqueName, authenticatedUser);

        //then
        InOrder inOrder = inOrder(fileMetadataService, fileStorageService);
        inOrder.verify(fileMetadataService).deleteFileAndUpdateUserStatus(file);
        inOrder.verify(fileStorageService).deleteFileYandexS3(uniqueName);
    }

    @Test
    @DisplayName("Удаление из БД остаётся в силе и исключение не пробрасывается при сбое S3")
    void shouldKeepDeletion_whenS3CleanupFails() {
        //given
        FileMetadata file = buildFileMetadata();
        String uniqueName = file.getUniqueName();

        when(fileMetadataService.findByUniqueName(uniqueName)).thenReturn(file);
        doThrow(new RuntimeException()).when(fileStorageService).deleteFileYandexS3(uniqueName);

        //when
        fileFacadeService.deleteFile(uniqueName, authenticatedUser);

        //then
        InOrder inOrder = inOrder(fileMetadataService, fileStorageService);
        inOrder.verify(fileMetadataService).deleteFileAndUpdateUserStatus(file);
        inOrder.verify(fileStorageService).deleteFileYandexS3(uniqueName);
    }

    @Test
    @DisplayName("Бросает FileAccessDeniedException и не трогает файл при удалении чужого")
    void shouldThrowFileAccessDeniedException_whenUserIsNotOwner() {
        //given
        FileMetadata file = buildFileMetadata();
        String uniqueName = file.getUniqueName();

        when(fileMetadataService.findByUniqueName(uniqueName)).thenReturn(file);
        doThrow(new FileAccessDeniedException())
                .when(filePermissionService).checkDeletePermission(authenticatedUser, file);

        //when //then
        assertThatThrownBy(() -> fileFacadeService.deleteFile(uniqueName, authenticatedUser))
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
