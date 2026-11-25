package org.resume.s3filemanager.service.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.common.model.ScanStatus;
import org.resume.s3filemanager.dto.AdminFileResponse;
import org.resume.s3filemanager.dto.FileStatsResponse;
import org.resume.s3filemanager.entity.FileMetadata;
import org.resume.s3filemanager.exception.FileNotFoundException;
import org.resume.s3filemanager.exception.InvalidScanStatusException;
import org.resume.s3filemanager.mapper.FileMapper;
import org.resume.s3filemanager.repository.FileMetadataRepository;
import org.resume.s3filemanager.service.file.YandexStorageService;
import org.resume.s3filemanager.service.kafka.OutboxService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис административных операций с файлами.
 * <p>
 * Предоставляет функционал просмотра файлов по статусу
 * и повторного сканирования.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFileService {

    private final FileMetadataRepository fileMetadataRepository;
    private final OutboxService outboxService;
    private final YandexStorageService yandexStorageService;
    private final FileMapper fileMapper;

    public Page<AdminFileResponse> findAllByScanStatus(ScanStatus scanStatus, Pageable pageable) {
        return fileMetadataRepository.findByScanStatusWithUser(scanStatus, pageable)
                .map(fileMapper::toAdminFileResponse);
    }

    /**
     * Повторно отправляет файл на сканирование.
     * Доступно только для файлов со статусом ERROR.
     *
     * @param fileId ID файла
     * @return обновлённая информация о файле
     */
    @Transactional
    public AdminFileResponse retryScan(Long fileId) {
        FileMetadata file = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException(fileId.toString()));

        if (file.getScanStatus() != ScanStatus.ERROR) {
            throw new InvalidScanStatusException(file.getScanStatus());
        }

        file.setScanStatus(ScanStatus.PENDING_SCAN);
        fileMetadataRepository.save(file);

        outboxService.saveFileUploadEvent(file, file.getUser().getId());

        return fileMapper.toAdminFileResponse(file);
    }

    /**
     * Возвращает статистику файлов по статусам сканирования.
     *
     * @return статистика по каждому статусу
     */
    public FileStatsResponse getFileStats() {
        return FileStatsResponse.builder()
                .pending(fileMetadataRepository.countByScanStatus(ScanStatus.PENDING_SCAN))
                .clean(fileMetadataRepository.countByScanStatus(ScanStatus.CLEAN))
                .infected(fileMetadataRepository.countByScanStatus(ScanStatus.INFECTED))
                .error(fileMetadataRepository.countByScanStatus(ScanStatus.ERROR))
                .total(fileMetadataRepository.count())
                .build();
    }

    /**
     * Удаляет все файлы с указанным статусом сканирования: сначала метаданные
     * одной транзакцией БД, затем объекты из S3 вне транзакции.
     * S3 delete идемпотентен: отсутствующий объект не ошибка.
     * При сбое удаления из S3 в бакете остаётся сирота - фиксируется в логе.
     *
     * @param scanStatus статус для удаления
     * @return количество удалённых записей
     */
    public int deleteAllByScanStatus(ScanStatus scanStatus) {
        List<FileMetadata> files = fileMetadataRepository.findAllByScanStatus(scanStatus);

        fileMetadataRepository.deleteAll(files);
        deleteFromStorage(files);

        return files.size();
    }

    /**
     * Удаляет файлы из S3 по одному; сбой на файле не мешает очистке остальных.
     */
    private void deleteFromStorage(List<FileMetadata> files) {
        for (FileMetadata file : files) {
            try {
                yandexStorageService.deleteFileYandexS3(file.getUniqueName());
            } catch (Exception e) {
                log.error("Metadata deleted but S3 cleanup failed, orphan object left: uniqueName={}",
                        file.getUniqueName(), e);
            }
        }
    }
}
