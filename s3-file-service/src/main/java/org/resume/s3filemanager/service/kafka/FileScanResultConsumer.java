package org.resume.s3filemanager.service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.common.model.FileScanResult;
import org.resume.s3filemanager.config.KafkaRetryConfig;
import org.resume.s3filemanager.exception.FileNotFoundException;
import org.resume.s3filemanager.service.file.FileScanResultService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer результатов антивирусного сканирования.
 * <p>
 * Слушает topic file-scan-results и обновляет статус файла в БД.
 * При отсутствии файла в БД — пропускает сообщение без ошибки.
 * При других ошибках — пробрасывает exception для retry, затем DLT (см. {@link KafkaRetryConfig}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileScanResultConsumer {

    private final FileScanResultService fileScanResultService;

    @KafkaListener(
            topics = "${kafka.topics.file-scan-results}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeScanResult(FileScanResult result) {
        log.info("Processing scan result: s3Key={}, status={}", result.getS3Key(), result.getStatus());

        try {
            fileScanResultService.processScanResult(result);
        } catch (FileNotFoundException e) {
            log.warn("File not found, skipping: s3Key={}", result.getS3Key());
        }
    }
}
