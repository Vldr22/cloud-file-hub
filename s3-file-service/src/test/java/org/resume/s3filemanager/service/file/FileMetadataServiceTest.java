package org.resume.s3filemanager.service.file;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.common.model.ScanStatus;
import org.resume.s3filemanager.entity.FileMetadata;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.FileUploadStatus;
import org.resume.s3filemanager.exception.FileNotFoundException;
import org.resume.s3filemanager.repository.FileMetadataRepository;
import org.resume.s3filemanager.service.auth.UserService;
import org.resume.s3filemanager.service.kafka.OutboxService;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileMetadataService — управление метаданными файлов")
class FileMetadataServiceTest {

    private static final Faker FAKER = new Faker();

    @Mock
    private FileMetadataRepository fileMetadataRepository;

    @Mock
    private FilePermissionService fileUploadPermissionService;

    @Mock
    private UserService userService;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private FileMetadataService fileMetadataService;

    private User user;
    private FileMetadata fileMetadata;
    private MockMultipartFile multipartFile;
    private String uniqueName;
    private String fileHash;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(FAKER.number().randomNumber());
        user.setUsername(FAKER.name().username());

        uniqueName = FAKER.internet().uuid() + ".pdf";
        fileHash = FAKER.internet().uuid();

        fileMetadata = new FileMetadata();
        fileMetadata.setUniqueName(uniqueName);
        fileMetadata.setOriginalName(FAKER.file().fileName());
        fileMetadata.setType("application/pdf");
        fileMetadata.setSize(FAKER.number().numberBetween(1L, 10_000_000L));
        fileMetadata.setFileHash(fileHash);
        fileMetadata.setUser(user);

        multipartFile = new MockMultipartFile(
                "file",
                FAKER.file().fileName(null, null, "pdf", null),
                "application/pdf",
                FAKER.lorem().characters(10).getBytes());
    }

    /**
     * Сохранение метаданных: объект строится с правильными полями и сохраняется в репозиторий.
     */
    @Test
    void shouldSaveMetadata_whenValidFileProvided() {
        when(fileMetadataRepository.save(any())).thenReturn(fileMetadata);

        FileMetadata result = fileMetadataService.saveDatabaseMetadata(multipartFile, uniqueName, fileHash, user);

        assertThat(result.getUniqueName()).isEqualTo(uniqueName);
        assertThat(result.getFileHash()).isEqualTo(fileHash);
        assertThat(result.getScanStatus()).isEqualTo(ScanStatus.PENDING_SCAN);
        verify(fileMetadataRepository).save(any());
    }

    /**
     * Сохранение с outbox-событием: метаданные, статус загрузки пользователя и событие на проверку.
     */
    @Test
    void shouldSaveMarkUploadedAndCreateOutboxEvent_whenSaveFileWithOutboxEvent() {
        when(fileMetadataRepository.save(any())).thenReturn(fileMetadata);

        FileMetadata result = fileMetadataService.saveFileWithOutboxEvent(multipartFile, uniqueName, fileHash, user);

        assertThat(result).isEqualTo(fileMetadata);
        verify(fileMetadataRepository).save(any());
        verify(fileUploadPermissionService).markFileUploaded(user.getId());
        verify(outboxService).saveFileUploadEvent(fileMetadata, user.getId());
    }

    /**
     * Удаление файла: метаданные удаляются, статус пользователя сбрасывается на NOT_UPLOADED.
     */
    @Test
    void shouldDeleteAndResetUserStatus_whenDeletingFile() {
        when(fileMetadataRepository.deleteByUniqueName(uniqueName)).thenReturn(1);

        fileMetadataService.deleteFileAndUpdateUserStatus(fileMetadata);

        verify(fileMetadataRepository).deleteByUniqueName(uniqueName);
        verify(userService).updateUploadStatus(user, FileUploadStatus.NOT_UPLOADED);
    }

    /**
     * Файл не найден при удалении: FileNotFoundException.
     */
    @Test
    void shouldThrowFileNotFoundException_whenDeletingNonExistentFile() {
        when(fileMetadataRepository.deleteByUniqueName(uniqueName)).thenReturn(0);

        assertThatThrownBy(() -> fileMetadataService.deleteDatabaseMetadata(uniqueName))
                .isInstanceOf(FileNotFoundException.class);
    }

    /**
     * Обновление статуса сканирования: файл сохраняется с новым статусом.
     */
    @Test
    void shouldUpdateScanStatus_whenFileExists() {
        when(fileMetadataRepository.findByUniqueName(uniqueName)).thenReturn(Optional.of(fileMetadata));

        fileMetadataService.updateScanStatus(uniqueName, ScanStatus.CLEAN);

        assertThat(fileMetadata.getScanStatus()).isEqualTo(ScanStatus.CLEAN);
        verify(fileMetadataRepository).save(fileMetadata);
    }

    /**
     * Файл не найден: FileNotFoundException.
     */
    @Test
    void shouldThrowFileNotFoundException_whenFileNotFound() {
        when(fileMetadataRepository.findByUniqueName(uniqueName)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileMetadataService.findByUniqueName(uniqueName))
                .isInstanceOf(FileNotFoundException.class);
    }
}