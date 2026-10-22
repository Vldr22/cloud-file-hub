package org.resume.s3filemanager.service.file;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.common.model.ScanStatus;
import org.resume.s3filemanager.entity.FileMetadata;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.FileUploadStatus;
import org.resume.s3filemanager.exception.FileNotFoundException;
import org.resume.s3filemanager.repository.FileMetadataRepository;
import org.resume.s3filemanager.service.auth.UserService;
import org.resume.s3filemanager.service.kafka.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Сервис для управления метаданными файлов в базе данных.
 * <p>
 * Обрабатывает CRUD операции для метаданных файлов и координирует работу
 * с сервисом пользователей для обновления статусов загрузки.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileMetadataService {

    private final FileMetadataRepository fileMetadataRepository;
    private final FilePermissionService fileUploadPermissionService;
    private final UserService userService;
    private final OutboxService outboxService;

    /**
     * Сохраняет метаданные файла со статусом PENDING_SCAN.
     *
     * @param contentType тип файла, определённый Tika по содержимому
     */
    public FileMetadata saveDatabaseMetadata(MultipartFile file, String uniqueFileName, String fileHash,
                                             String contentType, User user) {
        FileMetadata metadata = FileMetadata.builder()
                .uniqueName(uniqueFileName)
                .originalName(file.getOriginalFilename())
                .type(contentType)
                .size(file.getSize())
                .fileHash(fileHash)
                .scanStatus(ScanStatus.PENDING_SCAN)
                .user(user)
                .build();

        return fileMetadataRepository.save(metadata);
    }

    /**
     * Удаляет метаданные файла и обновляет статус загрузки пользователя если он не является Админом.
     * <p>
     * Сбрасывает статус пользователя на NOT_UPLOADED, позволяя загружать новые файлы.
     *
     * @param file метаданные файла для удаления
     */
    @Transactional
    public void deleteFileAndUpdateUserStatus(FileMetadata file) {
        deleteDatabaseMetadata(file.getUniqueName());
        userService.updateUploadStatus(file.getUser(), FileUploadStatus.NOT_UPLOADED);
    }

    /**
     * Сохраняет метаданные файла, отмечает загрузку у пользователя и создаёт outbox-событие
     * в одной транзакции: файл не может оказаться в БД без события на антивирусную проверку.
     */
    @Transactional
    public FileMetadata saveFileWithOutboxEvent(MultipartFile file, String uniqueFileName, String fileHash,
                                                String contentType, User user) {
        FileMetadata saved = saveDatabaseMetadata(file, uniqueFileName, fileHash, contentType, user);
        fileUploadPermissionService.markFileUploaded(user.getId());
        outboxService.saveFileUploadEvent(saved, user.getId());
        return saved;
    }

    @Transactional
    public void deleteDatabaseMetadata(String uniqueFileName) {
        int deleted = fileMetadataRepository.deleteByUniqueName(uniqueFileName);
        if (deleted == 0) {
            log.warn("File metadata not found for deletion: {}", uniqueFileName);
            throw new FileNotFoundException(uniqueFileName);
        }
    }

    @Transactional
    public void updateScanStatus(String uniqueName, ScanStatus status) {
        FileMetadata file = findByUniqueName(uniqueName);
        file.setScanStatus(status);
        fileMetadataRepository.save(file);
    }

    public FileMetadata findByUniqueName(String uniqueFileName) {
        return fileMetadataRepository.findByUniqueName(uniqueFileName)
                .orElseThrow(() -> new FileNotFoundException(uniqueFileName));
    }
}
