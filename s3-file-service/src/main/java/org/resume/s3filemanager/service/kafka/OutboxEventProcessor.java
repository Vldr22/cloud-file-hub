package org.resume.s3filemanager.service.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.common.model.FileUploadEvent;
import org.resume.common.model.ScanStatus;
import org.resume.s3filemanager.entity.OutboxEvent;
import org.resume.s3filemanager.enums.OutboxStatus;
import org.resume.s3filemanager.exception.KafkaSendException;
import org.resume.s3filemanager.properties.OutboxProperties;
import org.resume.s3filemanager.repository.FileMetadataRepository;
import org.resume.s3filemanager.repository.OutboxRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Обрабатывает одно Outbox событие в отдельной транзакции.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxEventProcessor {

    private final FileEventProducer fileEventProducer;
    private final ObjectMapper objectMapper;
    private final OutboxProperties outboxProperties;
    private final OutboxRepository outboxRepository;
    private final FileMetadataRepository fileMetadataRepository;

    /**
     * Обрабатывает одно событие в отдельной транзакции.
     * При успехе — помечает SENT, при ошибке — обновляет retry счётчик.
     * После исчерпания попыток ставит файлу ERROR.
     *
     * @param event событие для обработки
     */
    @Transactional(noRollbackFor = {KafkaSendException.class, JsonProcessingException.class})
    public void process(OutboxEvent event) {
        try {
            FileUploadEvent fileUploadEvent = objectMapper.readValue(
                    event.getPayload(),
                    FileUploadEvent.class
            );

            fileEventProducer.sendFileUploadEvent(fileUploadEvent);

            event.setStatus(OutboxStatus.SENT);
            event.setSentAt(Instant.now());
            outboxRepository.save(event);

            log.info("Outbox event sent: id={}, fileId={}", event.getId(), fileUploadEvent.getFileId());

        } catch (KafkaSendException e) {
            handleFailedEvent(event, e);
            if (event.getStatus() == OutboxStatus.FAILED) {
                markFileScanError(e.getFileId());
            }
        } catch (JsonProcessingException e) {
            handleFailedEvent(event, e);
        }
    }

    /**
     * Ставит файлу ERROR, чтобы админ мог перезапустить скан.
     */
    private void markFileScanError(Long fileId) {
        fileMetadataRepository.findById(fileId).ifPresent(file -> {
            file.setScanStatus(ScanStatus.ERROR);
            log.warn("File marked as scan error after outbox failure: fileId={}", fileId);
        });
    }

    private void handleFailedEvent(OutboxEvent event, Exception e) {
        int newRetryCount = event.getRetryCount() + 1;
        event.setRetryCount(newRetryCount);

        if (newRetryCount >= outboxProperties.getMaxRetryCount()) {
            event.setStatus(OutboxStatus.FAILED);
            log.error("Outbox event permanently failed after {} retries: id={}",
                    outboxProperties.getMaxRetryCount(), event.getId(), e);
        } else {
            long backoffMinutes = outboxProperties.getBaseBackoffMinutes() * (1L << newRetryCount);
            event.setNextRetryAt(Instant.now().plus(backoffMinutes, ChronoUnit.MINUTES));
            log.warn("Outbox event failed, retry {}/{} in {} minutes: id={}, cause={}",
                    newRetryCount, outboxProperties.getMaxRetryCount(), backoffMinutes, event.getId(), e.getMessage());
        }

        outboxRepository.save(event);
    }
}