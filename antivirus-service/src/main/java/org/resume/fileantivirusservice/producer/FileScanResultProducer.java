package org.resume.fileantivirusservice.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.common.model.FileScanResult;
import org.resume.common.model.ScanStatus;
import org.resume.common.properties.KafkaProperties;
import org.resume.fileantivirusservice.constant.ErrorMessages;
import org.resume.fileantivirusservice.exception.TechnicalException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Producer для отправки результатов сканирования файлов в Kafka.
 * <p>
 * Отправляет результаты в топик file-scan-results для последующей
 * обработки в s3-file-service (обновление статуса файла в БД).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(KafkaProperties.class)
public class FileScanResultProducer {

    private static final int SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, FileScanResult> kafkaTemplate;
    private final KafkaProperties kafkaProperties;

    /**
     * Отправляет результат сканирования файла в Kafka.
     * <p>
     * Отправка синхронная: при ошибке бросает {@link TechnicalException}, чтобы сработал retry.
     *
     * @param scanResult результат сканирования файла
     */
    public void sendScanResult(FileScanResult scanResult) {
        String topic = kafkaProperties.getTopics().getFileScanResults();

        log.info("Sending scan result to Kafka: s3Key={}, status={}",
                scanResult.getS3Key(), scanResult.getStatus());

        try {
            SendResult<String, FileScanResult> result = kafkaTemplate
                    .send(topic, scanResult.getS3Key(), scanResult)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.debug("Scan result sent successfully: s3Key={}, offset={}",
                    scanResult.getS3Key(), result.getRecordMetadata().offset());

        } catch (ExecutionException | TimeoutException e) {
            log.error("Failed to send scan result: s3Key={}, status={}",
                    scanResult.getS3Key(), scanResult.getStatus(), e);
            throw new TechnicalException(ErrorMessages.KAFKA_SEND_FAILED, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Scan result send interrupted: s3Key={}", scanResult.getS3Key(), e);
            throw new TechnicalException(ErrorMessages.KAFKA_SEND_FAILED, e);
        }
    }

    /**
     * Отправляет результат с ошибкой сканирования в Kafka.
     * <p>
     * Используется когда сканирование не удалось после всех retry попыток.
     *
     * @param s3Key ключ файла в S3
     * @param errorMessage сообщение об ошибке
     */
    public void sendErrorScanResult(String s3Key, String errorMessage) {
        FileScanResult errorResult = FileScanResult.builder()
                .s3Key(s3Key)
                .status(ScanStatus.ERROR)
                .errorMessage(errorMessage)
                .scannedAt(Instant.now())
                .build();

        sendScanResult(errorResult);
    }

}
