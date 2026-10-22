package org.resume.s3filemanager.service.admin;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.common.model.ScanStatus;
import org.resume.s3filemanager.dto.AdminFileResponse;
import org.resume.s3filemanager.dto.FileStatsResponse;
import org.resume.s3filemanager.entity.FileMetadata;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.UserRole;
import org.resume.s3filemanager.exception.FileNotFoundException;
import org.resume.s3filemanager.exception.InvalidScanStatusException;
import org.resume.s3filemanager.mapper.FileMapper;
import org.resume.s3filemanager.mapper.FileMapperImpl;
import org.resume.s3filemanager.repository.FileMetadataRepository;
import org.resume.s3filemanager.service.file.YandexStorageService;
import org.resume.s3filemanager.service.kafka.OutboxService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminFileService — административные операции с файлами")
class AdminFileServiceTest {

    private static final Faker FAKER = new Faker();

    @Mock
    private FileMetadataRepository fileMetadataRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private YandexStorageService yandexStorageService;

    @Spy
    private FileMapper fileMapper = new FileMapperImpl();

    @InjectMocks
    private AdminFileService adminFileService;

    private FileMetadata fileMetadata;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(FAKER.number().randomNumber());
        user.setUsername(FAKER.name().username());
        user.setRole(UserRole.USER);

        fileMetadata = new FileMetadata();
        fileMetadata.setId(FAKER.number().randomNumber());
        fileMetadata.setUniqueName(FAKER.internet().uuid() + ".pdf");
        fileMetadata.setOriginalName(FAKER.file().fileName(null, null, "pdf", null));
        fileMetadata.setType("application/pdf");
        fileMetadata.setSize(FAKER.number().numberBetween(1L, 10_000_000L));
        fileMetadata.setScanStatus(ScanStatus.CLEAN);
        fileMetadata.setUser(user);
    }

    /**
     * Список файлов по статусу: возвращает страницу с DTO.
     */
    @Test
    void shouldReturnFilePage_whenFindAllByScanStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        when(fileMetadataRepository.findByScanStatusWithUser(ScanStatus.CLEAN, pageable))
                .thenReturn(new PageImpl<>(List.of(fileMetadata)));

        Page<AdminFileResponse> result = adminFileService.findAllByScanStatus(ScanStatus.CLEAN, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().uniqueName()).isEqualTo(fileMetadata.getUniqueName());
        assertThat(result.getContent().getFirst().scanStatus()).isEqualTo(ScanStatus.CLEAN);
        assertThat(result.getContent().getFirst().uploadedUserName()).isEqualTo(user.getUsername());
        verify(fileMetadataRepository).findByScanStatusWithUser(ScanStatus.CLEAN, pageable);
    }

    /**
     * Повторное сканирование: статус меняется на PENDING_SCAN и событие публикуется.
     */
    @Test
    void shouldRetryScan_whenFileStatusIsError() {
        fileMetadata.setScanStatus(ScanStatus.ERROR);
        when(fileMetadataRepository.findById(fileMetadata.getId()))
                .thenReturn(Optional.of(fileMetadata));

        AdminFileResponse result = adminFileService.retryScan(fileMetadata.getId());

        assertThat(result.scanStatus()).isEqualTo(ScanStatus.PENDING_SCAN);
        verify(fileMetadataRepository).save(fileMetadata);
        verify(outboxService).saveFileUploadEvent(fileMetadata, fileMetadata.getUser().getId());
    }

    /**
     * Повторное сканирование: файл не найден, FileNotFoundException.
     */
    @Test
    void shouldThrowFileNotFoundException_whenFileNotFound() {
        when(fileMetadataRepository.findById(fileMetadata.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminFileService.retryScan(fileMetadata.getId()))
                .isInstanceOf(FileNotFoundException.class);
    }

    /**
     * Повторное сканирование: статус не ERROR, InvalidScanStatusException.
     */
    @Test
    void shouldThrowInvalidScanStatusException_whenFileStatusIsNotError() {
        fileMetadata.setScanStatus(ScanStatus.CLEAN);
        when(fileMetadataRepository.findById(fileMetadata.getId()))
                .thenReturn(Optional.of(fileMetadata));

        assertThatThrownBy(() -> adminFileService.retryScan(fileMetadata.getId()))
                .isInstanceOf(InvalidScanStatusException.class);

        verify(fileMetadataRepository, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    /**
     * Статистика файлов: возвращает количество по каждому статусу.
     */
    @Test
    void shouldReturnFileStats_whenGetFileStats() {
        when(fileMetadataRepository.countByScanStatus(ScanStatus.PENDING_SCAN)).thenReturn(3L);
        when(fileMetadataRepository.countByScanStatus(ScanStatus.CLEAN)).thenReturn(10L);
        when(fileMetadataRepository.countByScanStatus(ScanStatus.INFECTED)).thenReturn(1L);
        when(fileMetadataRepository.countByScanStatus(ScanStatus.ERROR)).thenReturn(2L);
        when(fileMetadataRepository.count()).thenReturn(16L);

        FileStatsResponse result = adminFileService.getFileStats();

        assertThat(result.pending()).isEqualTo(3L);
        assertThat(result.clean()).isEqualTo(10L);
        assertThat(result.infected()).isEqualTo(1L);
        assertThat(result.error()).isEqualTo(2L);
        assertThat(result.total()).isEqualTo(16L);
    }

    /**
     * Удаление файлов не INFECTED: удаляет из S3 и метаданные.
     */
    @Test
    void shouldDeleteFromS3AndDb_whenStatusIsNotInfected() {
        FileMetadata second = new FileMetadata();
        second.setUniqueName(FAKER.internet().uuid() + ".pdf");
        second.setUser(user);

        when(fileMetadataRepository.findAllByScanStatus(ScanStatus.ERROR))
                .thenReturn(List.of(fileMetadata, second));

        int deleted = adminFileService.deleteAllByScanStatus(ScanStatus.ERROR);

        assertThat(deleted).isEqualTo(2);
        verify(yandexStorageService).deleteFileYandexS3(fileMetadata.getUniqueName());
        verify(yandexStorageService).deleteFileYandexS3(second.getUniqueName());
        verify(fileMetadataRepository).deleteAll(List.of(fileMetadata, second));
    }

    /**
     * Удаление INFECTED файлов: только метаданные, S3 не трогаем.
     */
    @Test
    void shouldDeleteOnlyMetadata_whenStatusIsInfected() {
        fileMetadata.setScanStatus(ScanStatus.INFECTED);
        when(fileMetadataRepository.findAllByScanStatus(ScanStatus.INFECTED))
                .thenReturn(List.of(fileMetadata));

        int deleted = adminFileService.deleteAllByScanStatus(ScanStatus.INFECTED);

        assertThat(deleted).isEqualTo(1);
        verifyNoInteractions(yandexStorageService);
        verify(fileMetadataRepository).deleteAll(List.of(fileMetadata));
    }
}