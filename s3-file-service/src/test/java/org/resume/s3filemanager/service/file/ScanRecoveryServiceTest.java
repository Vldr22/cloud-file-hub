package org.resume.s3filemanager.service.file;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.common.model.ScanStatus;
import org.resume.s3filemanager.entity.FileMetadata;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.properties.ScanRecoveryProperties;
import org.resume.s3filemanager.repository.FileMetadataRepository;
import org.resume.s3filemanager.service.kafka.OutboxService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScanRecoveryService - восстановление сканирования зависших файлов")
class ScanRecoveryServiceTest {

    private static final Faker FAKER = new Faker();
    private static final int MAX_ATTEMPTS = 3;
    private static final int BATCH_SIZE = 50;

    @Mock
    private FileMetadataRepository fileMetadataRepository;

    @Mock
    private OutboxService outboxService;

    private ScanRecoveryService scanRecoveryService;

    private User user;

    @BeforeEach
    void setUp() {
        ScanRecoveryProperties properties = new ScanRecoveryProperties(
                Duration.ofMinutes(5),
                MAX_ATTEMPTS,
                BATCH_SIZE,
                300_000L,
                Duration.ofMinutes(4));

        scanRecoveryService = new ScanRecoveryService(fileMetadataRepository, outboxService, properties);

        user = new User();
        user.setId(FAKER.number().randomNumber());
        user.setUsername(FAKER.name().username());
    }

    @Test
    @DisplayName("Переотправляет зависший файл на сканирование через outbox и увеличивает счётчик")
    void shouldRepublishScanEvent_whenRetryLimitNotExceeded() {
        //given
        FileMetadata file = stuckFile(0);
        when(fileMetadataRepository.findByScanStatusAndUploadedAtBefore(
                eq(ScanStatus.PENDING_SCAN), any(Instant.class), eq(PageRequest.of(0, BATCH_SIZE))))
                .thenReturn(List.of(file));

        //when
        scanRecoveryService.recoverStuckFiles();

        //then
        assertThat(file.getRetryCount()).isEqualTo(1);
        assertThat(file.getScanStatus()).isEqualTo(ScanStatus.PENDING_SCAN);
        verify(outboxService).saveFileUploadEvent(file, user.getId());
        verify(fileMetadataRepository).saveAll(List.of(file));
    }

    @Test
    @DisplayName("Переводит файл в ERROR без переотправки, когда лимит попыток исчерпан")
    void shouldMarkError_whenRetryLimitExceeded() {
        //given
        FileMetadata file = stuckFile(MAX_ATTEMPTS);
        when(fileMetadataRepository.findByScanStatusAndUploadedAtBefore(
                eq(ScanStatus.PENDING_SCAN), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(file));

        //when
        scanRecoveryService.recoverStuckFiles();

        //then
        assertThat(file.getScanStatus()).isEqualTo(ScanStatus.ERROR);
        assertThat(file.getRetryCount()).isEqualTo(MAX_ATTEMPTS + 1);
        verifyNoInteractions(outboxService);
        verify(fileMetadataRepository).saveAll(List.of(file));
    }

    @Test
    @DisplayName("Ничего не делает, когда зависших файлов нет")
    void shouldDoNothing_whenNoStuckFiles() {
        //given
        when(fileMetadataRepository.findByScanStatusAndUploadedAtBefore(
                eq(ScanStatus.PENDING_SCAN), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        //when
        scanRecoveryService.recoverStuckFiles();

        //then
        verifyNoInteractions(outboxService);
        verify(fileMetadataRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Обрабатывает смешанную пачку: одни файлы переотправляет, другие переводит в ERROR")
    void shouldProcessWholeBatch_whenFilesInMixedState() {
        //given
        FileMetadata recoverable = stuckFile(1);
        FileMetadata hopeless = stuckFile(MAX_ATTEMPTS);
        when(fileMetadataRepository.findByScanStatusAndUploadedAtBefore(
                eq(ScanStatus.PENDING_SCAN), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(recoverable, hopeless));

        //when
        scanRecoveryService.recoverStuckFiles();

        //then
        assertThat(recoverable.getScanStatus()).isEqualTo(ScanStatus.PENDING_SCAN);
        assertThat(recoverable.getRetryCount()).isEqualTo(2);
        assertThat(hopeless.getScanStatus()).isEqualTo(ScanStatus.ERROR);
        verify(outboxService).saveFileUploadEvent(recoverable, user.getId());
        verifyNoMoreInteractions(outboxService);
        verify(fileMetadataRepository).saveAll(List.of(recoverable, hopeless));
    }

    private FileMetadata stuckFile(int retryCount) {
        FileMetadata file = new FileMetadata();
        file.setUniqueName(FAKER.internet().uuid() + ".pdf");
        file.setScanStatus(ScanStatus.PENDING_SCAN);
        file.setUser(user);
        file.setRetryCount(retryCount);
        return file;
    }
}
