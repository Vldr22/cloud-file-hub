package org.resume.s3filemanager.service.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.common.model.FileScanResult;
import org.resume.common.model.ScanStatus;
import org.springframework.stereotype.Service;

/**
 * Сервис для обработки результатов антивирусного сканирования.
 * <p>
 * Обрабатывает различные статусы сканирования:
 * <ul>
 *   <li>CLEAN — обновляет статус в БД</li>
 *   <li>INFECTED — блокирует файл статусом в БД, затем удаляет из S3</li>
 *   <li>ERROR — обновляет статус в БД</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileScanResultService {

    private final FileMetadataService fileMetadataService;
    private final YandexStorageService yandexStorageService;

    /**
     * Обрабатывает результат сканирования файла.
     * <p>
     * Не транзакционный: S3-вызов выполняется вне транзакции БД.
     *
     * @param result результат сканирования от antivirus-service
     */
    public void processScanResult(FileScanResult result) {
        String s3Key = result.getS3Key();
        ScanStatus status = result.getStatus();

        if (status == ScanStatus.INFECTED) {
            handleInfectedFile(result);
        } else {
            fileMetadataService.updateScanStatus(s3Key, status);
        }
    }

    /**
     * Обрабатывает заражённый файл: сначала блокирует его статусом INFECTED,
     * затем удаляет из S3. При сбое удаления объект остаётся в бакете,
     * но файл уже недоступен для скачивания.
     */
    private void handleInfectedFile(FileScanResult result) {
        String s3Key = result.getS3Key();

        log.warn("Infected file detected: s3Key={}, virus={}",
                s3Key, result.getVirusName());

        fileMetadataService.updateScanStatus(s3Key, ScanStatus.INFECTED);

        try {
            yandexStorageService.deleteFileYandexS3(s3Key);
            log.debug("Infected file deleted from S3: s3Key={}", s3Key);

        } catch (Exception e) {
            log.error("Infected file blocked but S3 deletion failed, orphan object left: s3Key={}",
                    s3Key, e);
        }
    }
}
