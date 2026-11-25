package org.resume.s3filemanager.service.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.common.model.ScanStatus;
import org.resume.s3filemanager.entity.FileMetadata;
import org.resume.s3filemanager.properties.ScanRecoveryProperties;
import org.resume.s3filemanager.repository.FileMetadataRepository;
import org.resume.s3filemanager.service.kafka.OutboxService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Сервис восстановления сканирования зависших файлов.
 * <p>
 * Файл в {@code PENDING_SCAN} дольше порога означает потерянный результат
 * сканирования: событие переотправляется через outbox. Файлы, исчерпавшие
 * лимит попыток, переводятся в {@code ERROR} - терминальное состояние,
 * из которого файл восстанавливает админ через повторное сканирование.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(ScanRecoveryProperties.class)
public class ScanRecoveryService {

    private final FileMetadataRepository fileMetadataRepository;
    private final OutboxService outboxService;
    private final ScanRecoveryProperties scanRecoveryProperties;

    /**
     * Возвращает зависшие файлы на сканирование одной транзакцией.
     */
    @Transactional
    public void recoverStuckFiles() {
        List<FileMetadata> stuckFiles = findStuckFiles();

        if (stuckFiles.isEmpty()) {
            return;
        }

        stuckFiles.forEach(this::recoverFile);
        fileMetadataRepository.saveAll(stuckFiles);

        log.info("Scan recovery completed: stuckFiles={}", stuckFiles.size());
    }

    private List<FileMetadata> findStuckFiles() {
        Instant cutoff = Instant.now().minus(scanRecoveryProperties.getThreshold());

        return fileMetadataRepository.findByScanStatusAndUploadedAtBefore(
                ScanStatus.PENDING_SCAN,
                cutoff,
                PageRequest.of(0, scanRecoveryProperties.getBatchSize()));
    }

    private void recoverFile(FileMetadata file) {
        file.setRetryCount(file.getRetryCount() + 1);

        if (file.getRetryCount() > scanRecoveryProperties.getMaxAttempts()) {
            file.setScanStatus(ScanStatus.ERROR);
            log.warn("Stuck file exceeded retry limit, marked ERROR: uniqueName={}, retryCount={}",
                    file.getUniqueName(), file.getRetryCount());
        } else {
            outboxService.saveFileUploadEvent(file, file.getUser().getId());
        }
    }
}
