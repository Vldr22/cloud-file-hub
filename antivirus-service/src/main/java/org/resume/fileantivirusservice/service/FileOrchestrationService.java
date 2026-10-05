package org.resume.fileantivirusservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.common.model.FileScanResult;
import org.resume.common.model.FileUploadEvent;
import org.resume.fileantivirusservice.constant.ErrorMessages;
import org.resume.fileantivirusservice.consumer.FileUploadConsumer;
import org.resume.fileantivirusservice.exception.TechnicalException;
import org.resume.fileantivirusservice.producer.FileScanResultProducer;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Сервис оркестрации процесса сканирования файлов.
 * <p>
 * Координирует работу сервисов скачивания, сканирования и отправки результатов.
 * Любые ошибки пробрасываются выше для обработки в {@link FileUploadConsumer}.
 *
 * @see S3YandexService
 * @see ClamAVService
 * @see FileScanResultProducer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileOrchestrationService {

    private final S3YandexService s3YandexService;
    private final ClamAVService clamAVService;
    private final FileScanResultProducer fileScanResultProducer;

    /**
     * Обрабатывает событие загрузки файла.
     * <p>
     * Выполняет полный цикл обработки:
     * <ol>
     *   <li>Скачивание файла из S3</li>
     *   <li>Антивирусное сканирование через ClamAV</li>
     *   <li>Отправка результата в Kafka</li>
     * </ol>
     *
     * @param event событие загрузки файла с метаданными
     */
    public void processFileUpload(FileUploadEvent event) {
        log.info("Processing file upload: fileId={}, s3Key={}", event.getFileId(), event.getS3Key());

        FileScanResult scanResult;
        try (InputStream fileStream = downloadFile(event.getS3Key())) {
            scanResult = scanFile(event.getS3Key(), fileStream);

        } catch (IOException e) {
            log.error("Failed to close S3 stream: s3Key={}", event.getS3Key(), e);
            throw new TechnicalException(ErrorMessages.S3_STREAM_CLOSE_FAILED, e);
        }

        sendScanResult(scanResult);

        log.info("File processing completed: fileId={}, status={}",
                event.getFileId(), scanResult.getStatus());
    }

    private InputStream downloadFile(String s3Key) {
        log.debug("Downloading file from S3: s3Key={}", s3Key);
        return s3YandexService.downloadFile(s3Key);
    }

    private FileScanResult scanFile(String s3Key, InputStream fileStream) {
        log.debug("Scanning file with ClamAV: s3Key={}", s3Key);
        return clamAVService.scanFile(s3Key, fileStream);
    }

    private void sendScanResult(FileScanResult scanResult) {
        log.debug("File scan completed: s3Key={}, status={}",
                scanResult.getS3Key(), scanResult.getStatus());
        fileScanResultProducer.sendScanResult(scanResult);
    }
}
