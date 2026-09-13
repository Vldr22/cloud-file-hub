package org.resume.s3filemanager.service.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.s3filemanager.audit.AuditOperation;
import org.resume.s3filemanager.audit.Auditable;
import org.resume.s3filemanager.audit.ResourceType;
import org.resume.s3filemanager.constant.SuccessMessages;
import org.resume.s3filemanager.dto.FileDownloadResponse;
import org.resume.s3filemanager.dto.MultipleUploadResponse;
import org.resume.s3filemanager.entity.FileMetadata;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.CommonResponseStatus;
import org.resume.s3filemanager.exception.*;
import org.resume.s3filemanager.service.file.strategy.ErrorResponseStrategyResolver;
import org.resume.s3filemanager.service.kafka.OutboxService;
import org.resume.s3filemanager.validation.FileValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Фасадный сервис для работы с файлами.
 * <p>
 * Координирует операции загрузки, скачивания и удаления файлов,
 * взаимодействуя с валидацией, s3 хранилищем и сервисами БД.
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
    private final OutboxService outboxService;
    private final ErrorResponseStrategyResolver errorResponseStrategyResolver;

    /**
     * Загружает один файл с проверкой прав пользователя.
     * <p>
     * Выполняет следующие операции:
     * <ul>
     *   <li>Проверяет права пользователя на загрузку</li>
     *   <li>Генерирует уникальное имя файла</li>
     *   <li>Проверяет наличие дубликатов (в рамках пользователя)</li>
     *   <li>Загружает файл в S3 хранилище</li>
     *   <li>Сохраняет метаданные в базу данных</li>
     * </ul>
     *
     * @param file загружаемый файл
     * @throws FileUploadLimitException если пользователь уже загрузил файл
     * @throws DuplicateFileException если файл с таким хешем уже существует у пользователя
     * @throws S3YandexException при ошибке загрузки в S3
     */
    @Auditable(operation = AuditOperation.FILE_UPLOAD, resourceType = ResourceType.FILE)
    public void uploadFile(MultipartFile file) {
        User user = filePermissionService.checkUploadPermission();
        uploadFileInternal(file, user);
    }

    /**
     * Множественная загрузка файлов (только для администратора).
     * <p>
     * Реализует паттерн частичного успеха: валидные файлы загружаются, в то время как
     * невалидные отклоняются с конкретными сообщениями об ошибках. Хотя бы один файл
     * должен быть успешно загружен.
     *
     * @param files загружаемые файлы; количество ограничено настройкой батча
     * @return список результатов загрузки для каждого файла со статусом и сообщением
     * @throws MultipleFileUploadException если все файлы не прошли загрузку
     */
    public List<MultipleUploadResponse> multipleUpload(MultipartFile[] files) {
        User admin = filePermissionService.checkUploadPermission();

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
     * Скачивает файл по уникальному имени
     *
     * @param uniqueName the UUID-based unique filename
     * @return file download response with content and metadata
     * @throws FileNotFoundException if file metadata not found in database
     * @throws S3YandexException if S3 download fails
     */
    @Auditable(operation = AuditOperation.FILE_DOWNLOAD, resourceType = ResourceType.FILE)
    public FileDownloadResponse downloadFile(String uniqueName) {
        FileMetadata metadata = fileMetadataService.findByUniqueName(uniqueName);
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
     * Удаляет файл с проверкой прав владения.
     * <p>
     * Пользователи могут удалять только свои файлы. Администраторы могут удалять любые файлы.
     * Сбрасывает статус загрузки пользователя на NOT_UPLOADED, если владелец удаляет файл.
     *
     * @param uniqueName уникальное имя файла на основе UUID
     * @throws FileNotFoundException если файл не найден
     * @throws FileAccessDeniedException если пользователь пытается удалить чужой файл
     */
    @Auditable(operation = AuditOperation.FILE_DELETE, resourceType = ResourceType.FILE)
    public void deleteFile(String uniqueName) {
        User currentUser = filePermissionService.getCurrentUser();
        FileMetadata file = fileMetadataService.findByUniqueName(uniqueName);
        filePermissionService.checkDeletePermission(currentUser, file);

        fileStorageService.deleteFileYandexS3(uniqueName);
        fileMetadataService.deleteFileAndUpdateUserStatus(file);

        log.info("File deleted successfully: {}", uniqueName);
    }

    /**
     * Основная логика загрузки файла без проверки прав.
     * <p>
     * Реализует паттерн Saga: при ошибке сохранения метаданных
     * выполняется компенсирующая транзакция (удаление из S3).
     *
     * @param file загружаемый файл
     * @param user пользователь-владелец файла
     * @return уникальное имя загруженного файла
     */
    private String uploadFileInternal(MultipartFile file, User user) {
        String uniqueFileName = generateUniqueFileName(file.getOriginalFilename());
        byte[] fileBytes = readFileBytes(file);
        String fileHash = fileHashService.calculateMD5(fileBytes);

        fileHashService.checkDuplicateInDatabase(fileHash, user.getId());
        fileStorageService.uploadFileYandexS3(uniqueFileName, fileBytes, file.getContentType());

        try {
            FileMetadata saved = fileMetadataService.saveFileWithPermission(file, uniqueFileName, fileHash, user);
            log.info("File uploaded successfully: {}", uniqueFileName);

            outboxService.saveFileUploadEvent(saved, user.getId());

            return uniqueFileName;
        } catch (Exception e) {
            log.warn("DB save failed, rolling back S3 upload: {}", uniqueFileName);
            compensateS3Upload(uniqueFileName);
            throw e;
        }
    }

    /**
     * Обрабатывает один файл в рамках пакетной загрузки.
     * <p>
     * Выполняет валидацию и загрузку, возвращая результат
     * независимо от успеха (паттерн частичного успеха).
     */
    private MultipleUploadResponse processSingleFile(MultipartFile file, User admin) {
        try {

            Optional<String> validationError = fileValidator.validateFile(file);
            if (validationError.isPresent()) {
                return createValidationErrorResponse(file, validationError.get());
            }

            String uniqueName = uploadFileInternal(file, admin);
            return createSuccessResponse(file, uniqueName);

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

    private String generateUniqueFileName(String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        extension = (extension != null && !extension.isBlank()) ? extension : "tmp";
        return UUID.randomUUID() + "." + extension;
    }
}
