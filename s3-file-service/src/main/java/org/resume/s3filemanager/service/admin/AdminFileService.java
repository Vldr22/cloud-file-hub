package org.resume.s3filemanager.service.admin;

import lombok.RequiredArgsConstructor;
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
     * Удаляет все файлы с указанным статусом сканирования.
     * Для INFECTED — только метаданные (S3 уже очищен).
     * Для остальных — удаляет и из S3, и метаданные.
     *
     * @param scanStatus статус для удаления
     * @return количество удалённых записей
     */
    @Transactional
    public int deleteAllByScanStatus(ScanStatus scanStatus) {
        List<FileMetadata> files = fileMetadataRepository.findAllByScanStatus(scanStatus);

        if (scanStatus != ScanStatus.INFECTED) {
            files.forEach(file -> yandexStorageService.deleteFileYandexS3(file.getUniqueName()));
        }

        fileMetadataRepository.deleteAll(files);
        return files.size();
    }
}
