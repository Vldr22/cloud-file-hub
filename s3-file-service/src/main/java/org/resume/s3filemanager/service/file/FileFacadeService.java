package org.resume.s3filemanager.service.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.common.model.ScanStatus;
import org.resume.s3filemanager.audit.AuditOperation;
import org.resume.s3filemanager.audit.Auditable;
import org.resume.s3filemanager.audit.ResourceType;
import org.resume.s3filemanager.constant.SuccessMessages;
import org.resume.s3filemanager.dto.FileDownloadResponse;
import org.resume.s3filemanager.dto.FileResponse;
import org.resume.s3filemanager.dto.MultipleUploadResponse;
import org.resume.s3filemanager.entity.FileMetadata;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.CommonResponseStatus;
import org.resume.s3filemanager.exception.*;
import org.resume.s3filemanager.mapper.FileMapper;
import org.resume.s3filemanager.security.AuthenticatedUser;
import org.resume.s3filemanager.service.file.strategy.ErrorResponseStrategyResolver;
import org.resume.s3filemanager.validation.FileValidator;
import org.resume.s3filemanager.validation.TikaFileDetector;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Фасад загрузки, скачивания и удаления файлов: права, S3 и метаданные в БД.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileFacadeService {

    private final FileHashService fileHashService;
    private final YandexStorageService fileStorageService;
    private final FileMetadataService fileMetadataService;
    private final FilePermissionService filePermissionService;
    private final FileValidator fileValidator;
    private final TikaFileDetector tikaFileDetector;
    private final ErrorResponseStrategyResolver errorResponseStrategyResolver;
    private final S3KeyGenerator s3KeyGenerator;
    private final FileMapper fileMapper;

    /**
     * Загружает файл с проверкой лимита и дубликатов.
     *
     * @return файл со статусом PENDING_SCAN
     * @throws FileUploadLimitException если лимит загрузки исчерпан
     * @throws DuplicateFileException   если такой файл уже загружен пользователем
     */
    @Auditable(operation = AuditOperation.FILE_UPLOAD, resourceType = ResourceType.FILE)
    public FileResponse uploadFile(MultipartFile file, AuthenticatedUser authenticatedUser) {
        User user = filePermissionService.checkUploadPermission(authenticatedUser);
        return fileMapper.toFileResponse(uploadFileInternal(file, user));
    }

    /**
     * Пакетная загрузка с частичным успехом: ошибка одного файла не останавливает остальные.
     *
     * @return результат по каждому файлу
     * @throws MultipleFileUploadException если не загрузился ни один файл
     */
    public List<MultipleUploadResponse> multipleUpload(MultipartFile[] files, AuthenticatedUser authenticatedUser) {
        User admin = filePermissionService.checkUploadPermission(authenticatedUser);

        List<MultipleUploadResponse> results = new ArrayList<>();
        int successCount = 0;

        for (MultipartFile file : files) {
            MultipleUploadResponse response = processSingleFile(file, admin);
            results.add(response);

            if (response.status() == CommonResponseStatus.SUCCESS) {
                successCount++;
            }
        }

        if (successCount == 0) {
            throw new MultipleFileUploadException(results);
        }

        log.info("Batch upload completed: {}/{} successful", successCount, files.length);
        return results;
    }

    /**
     * Скачивает файл, прошедший антивирусную проверку.
     *
     * @throws FileNotFoundException     если файл не найден
     * @throws FileNotAvailableException если статус проверки не CLEAN
     */
    @Auditable(operation = AuditOperation.FILE_DOWNLOAD, resourceType = ResourceType.FILE)
    public FileDownloadResponse downloadFile(String uniqueName) {
        FileMetadata metadata = fileMetadataService.findByUniqueName(uniqueName);

        if (metadata.getScanStatus() != ScanStatus.CLEAN) {
            throw new FileNotAvailableException(metadata.getScanStatus());
        }

        byte[] data = fileStorageService.downloadFileYandexS3(uniqueName);

        String encodedFileName = URLEncoder.encode(
                metadata.getOriginalName(),
                StandardCharsets.UTF_8
        ).replace("+", "%20");

        return FileDownloadResponse.builder()
                .content(data)
                .fileName(encodedFileName)
                .contentType(metadata.getType())
                .size(data.length)
                .build();
    }

    /**
     * Удаляет файл из S3 и БД и сбрасывает лимит загрузки владельца.
     * Пользователь удаляет только свои файлы, админ любые.
     *
     * @throws FileNotFoundException     если файл не найден
     * @throws FileAccessDeniedException если пользователь удаляет чужой файл
     */
    @Auditable(operation = AuditOperation.FILE_DELETE, resourceType = ResourceType.FILE)
    public void deleteFile(String uniqueName, AuthenticatedUser authenticatedUser) {
        FileMetadata file = fileMetadataService.findByUniqueName(uniqueName);
        filePermissionService.checkDeletePermission(authenticatedUser, file);

        fileStorageService.deleteFileYandexS3(uniqueName);
        fileMetadataService.deleteFileAndUpdateUserStatus(file);

        log.info("File deleted successfully: {}", uniqueName);
    }

    /**
     * Загрузка без проверки прав: тип файла берётся от Tika, при ошибке сохранения в БД файл удаляется из S3.
     */
    private FileMetadata uploadFileInternal(MultipartFile file, User user) {
        String uniqueFileName = s3KeyGenerator.generate(file.getOriginalFilename());
        byte[] fileBytes = readFileBytes(file);
        String fileHash = fileHashService.calculateMD5(fileBytes);
        String contentType = tikaFileDetector.detectContentType(fileBytes, file.getOriginalFilename());

        fileHashService.checkDuplicateInDatabase(fileHash, user.getId());
        fileStorageService.uploadFileYandexS3(uniqueFileName, fileBytes, contentType);

        try {
            FileMetadata saved = fileMetadataService.saveFileWithOutboxEvent(
                    file, uniqueFileName, fileHash, contentType, user);
            log.info("File uploaded successfully: {}", uniqueFileName);

            return saved;
        } catch (Exception e) {
            log.warn("DB save failed, rolling back S3 upload: {}", uniqueFileName);
            compensateS3Upload(uniqueFileName);
            throw e;
        }
    }

    /**
     * Валидирует и загружает один файл пакета, ошибку возвращает результатом, а не исключением.
     */
    private MultipleUploadResponse processSingleFile(MultipartFile file, User admin) {
        try {
            Optional<String> validationError = fileValidator.validateFile(file);
            if (validationError.isPresent()) {
                return createValidationErrorResponse(file, validationError.get());
            }

            FileMetadata saved = uploadFileInternal(file, admin);
            return createSuccessResponse(file, saved.getUniqueName());

        } catch (Exception e) {
            return createExceptionErrorResponse(file, e);
        }
    }

    private MultipleUploadResponse createSuccessResponse(MultipartFile file, String uniqueName) {
        return new MultipleUploadResponse(
                CommonResponseStatus.SUCCESS,
                file.getOriginalFilename(),
                uniqueName,
                SuccessMessages.FILE_UPLOAD_SUCCESS
        );
    }

    private MultipleUploadResponse createValidationErrorResponse(MultipartFile file, String errorMessage) {
        log.warn("Validation failed for {}: {}", file.getOriginalFilename(), errorMessage);
        return new MultipleUploadResponse(
                CommonResponseStatus.ERROR,
                file.getOriginalFilename(),
                null,
                errorMessage
        );
    }

    private MultipleUploadResponse createExceptionErrorResponse(MultipartFile file, Exception e) {
        String errorMessage = errorResponseStrategyResolver.getStrategy(e).handle(file.getOriginalFilename(), e);

        return new MultipleUploadResponse(
                CommonResponseStatus.ERROR,
                file.getOriginalFilename(),
                null,
                errorMessage
        );
    }

    // Helper methods
    private byte[] readFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            log.error("Failed to read file: {}", file.getOriginalFilename(), ex);
            throw new FileReadException(ex, file.getOriginalFilename());
        }
    }

    private void compensateS3Upload(String fileName) {
        try {
            fileStorageService.deleteFileYandexS3(fileName);
            log.info("Successfully rolled back S3 upload: {}", fileName);
        } catch (Exception ex) {
            log.error("Failed to rollback S3 upload: {}", fileName, ex);
        }
    }
}
