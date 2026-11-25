package org.resume.s3filemanager.service.admin;

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

    @Test
    @DisplayName("Возвращает страницу файлов с DTO при поиске по статусу")
    void shouldReturnFilePage_whenFindAllByScanStatus() {
        //given
        Pageable pageable = PageRequest.of(0, 10);
        when(fileMetadataRepository.findByScanStatusWithUser(ScanStatus.CLEAN, pageable))
                .thenReturn(new PageImpl<>(List.of(fileMetadata)));

        //when
        Page<AdminFileResponse> result = adminFileService.findAllByScanStatus(ScanStatus.CLEAN, pageable);

        //then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().uniqueName()).isEqualTo(fileMetadata.getUniqueName());
        assertThat(result.getContent().getFirst().scanStatus()).isEqualTo(ScanStatus.CLEAN);
        assertThat(result.getContent().getFirst().uploadedUserName()).isEqualTo(user.getUsername());
        verify(fileMetadataRepository).findByScanStatusWithUser(ScanStatus.CLEAN, pageable);
    }

    @Test
    @DisplayName("Меняет статус на PENDING_SCAN, сбрасывает счётчик попыток и сохраняет outbox-событие при повторном сканировании")
    void shouldRetryScan_whenFileStatusIsError() {
        //given
        fileMetadata.setScanStatus(ScanStatus.ERROR);
        fileMetadata.setRetryCount(4);
        when(fileMetadataRepository.findById(fileMetadata.getId()))
                .thenReturn(Optional.of(fileMetadata));

        //when
        AdminFileResponse result = adminFileService.retryScan(fileMetadata.getId());

        //then
        assertThat(result.scanStatus()).isEqualTo(ScanStatus.PENDING_SCAN);
        assertThat(fileMetadata.getRetryCount()).isZero();
        verify(fileMetadataRepository).save(fileMetadata);
        verify(outboxService).saveFileUploadEvent(fileMetadata, fileMetadata.getUser().getId());
    }

    @Test
    @DisplayName("Бросает FileNotFoundException при повторном сканировании несуществующего файла")
    void shouldThrowFileNotFoundException_whenFileNotFound() {
        //given
        when(fileMetadataRepository.findById(fileMetadata.getId()))
                .thenReturn(Optional.empty());

        //when //then
        assertThatThrownBy(() -> adminFileService.retryScan(fileMetadata.getId()))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    @DisplayName("Бросает InvalidScanStatusException и не сохраняет событие, если статус файла не ERROR")
    void shouldThrowInvalidScanStatusException_whenFileStatusIsNotError() {
        //given
        fileMetadata.setScanStatus(ScanStatus.CLEAN);
        when(fileMetadataRepository.findById(fileMetadata.getId()))
                .thenReturn(Optional.of(fileMetadata));

        //when //then
        assertThatThrownBy(() -> adminFileService.retryScan(fileMetadata.getId()))
                .isInstanceOf(InvalidScanStatusException.class);

        verify(fileMetadataRepository, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    @DisplayName("Возвращает статистику файлов по каждому статусу сканирования")
    void shouldReturnFileStats_whenGetFileStats() {
        //given
        when(fileMetadataRepository.countByScanStatus(ScanStatus.PENDING_SCAN)).thenReturn(3L);
        when(fileMetadataRepository.countByScanStatus(ScanStatus.CLEAN)).thenReturn(10L);
        when(fileMetadataRepository.countByScanStatus(ScanStatus.INFECTED)).thenReturn(1L);
        when(fileMetadataRepository.countByScanStatus(ScanStatus.ERROR)).thenReturn(2L);
        when(fileMetadataRepository.count()).thenReturn(16L);

        //when
        FileStatsResponse result = adminFileService.getFileStats();

        //then
        assertThat(result.pending()).isEqualTo(3L);
        assertThat(result.clean()).isEqualTo(10L);
        assertThat(result.infected()).isEqualTo(1L);
        assertThat(result.error()).isEqualTo(2L);
        assertThat(result.total()).isEqualTo(16L);
    }

    @Test
    @DisplayName("Удаляет сначала метаданные из БД, затем объекты из S3")
    void shouldDeleteMetadataThenS3_whenDeleteAllByScanStatus() {
        //given
        FileMetadata second = new FileMetadata();
        second.setUniqueName(FAKER.internet().uuid() + ".pdf");
        second.setUser(user);

        when(fileMetadataRepository.findAllByScanStatus(ScanStatus.ERROR))
                .thenReturn(List.of(fileMetadata, second));

        //when
        int deleted = adminFileService.deleteAllByScanStatus(ScanStatus.ERROR);

        //then
        assertThat(deleted).isEqualTo(2);
        InOrder inOrder = inOrder(fileMetadataRepository, yandexStorageService);
        inOrder.verify(fileMetadataRepository).deleteAll(List.of(fileMetadata, second));
        inOrder.verify(yandexStorageService).deleteFileYandexS3(fileMetadata.getUniqueName());
        inOrder.verify(yandexStorageService).deleteFileYandexS3(second.getUniqueName());
    }

    @Test
    @DisplayName("Продолжает удаление остальных файлов из S3 при сбое на одном")
    void shouldContinueStorageCleanup_whenS3FailsForOneFile() {
        //given
        FileMetadata second = new FileMetadata();
        second.setUniqueName(FAKER.internet().uuid() + ".pdf");
        second.setUser(user);

        when(fileMetadataRepository.findAllByScanStatus(ScanStatus.ERROR))
                .thenReturn(List.of(fileMetadata, second));
        doThrow(new RuntimeException()).when(yandexStorageService)
                .deleteFileYandexS3(fileMetadata.getUniqueName());

        //when
        int deleted = adminFileService.deleteAllByScanStatus(ScanStatus.ERROR);

        //then
        assertThat(deleted).isEqualTo(2);
        verify(yandexStorageService).deleteFileYandexS3(second.getUniqueName());
    }

    @Test
    @DisplayName("Удаляет заражённые файлы из БД и S3 наравне с другими статусами")
    void shouldDeleteMetadataAndS3_whenStatusIsInfected() {
        //given
        fileMetadata.setScanStatus(ScanStatus.INFECTED);
        when(fileMetadataRepository.findAllByScanStatus(ScanStatus.INFECTED))
                .thenReturn(List.of(fileMetadata));

        //when
        int deleted = adminFileService.deleteAllByScanStatus(ScanStatus.INFECTED);

        //then
        assertThat(deleted).isEqualTo(1);
        InOrder inOrder = inOrder(fileMetadataRepository, yandexStorageService);
        inOrder.verify(fileMetadataRepository).deleteAll(List.of(fileMetadata));
        inOrder.verify(yandexStorageService).deleteFileYandexS3(fileMetadata.getUniqueName());
    }
}