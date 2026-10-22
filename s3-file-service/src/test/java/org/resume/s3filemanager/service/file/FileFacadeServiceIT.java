package org.resume.s3filemanager.service.file;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.resume.common.model.ScanStatus;
import org.resume.s3filemanager.BaseIntegrationTest;
import org.resume.s3filemanager.constant.MdcConstants;
import org.resume.s3filemanager.entity.FileMetadata;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.FileUploadStatus;
import org.resume.s3filemanager.enums.UserRole;
import org.resume.s3filemanager.enums.UserStatus;
import org.resume.s3filemanager.repository.AuditLogRepository;
import org.resume.s3filemanager.repository.FileMetadataRepository;
import org.resume.s3filemanager.repository.UserRepository;
import org.resume.s3filemanager.security.AuthenticatedUser;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class FileFacadeServiceIT extends BaseIntegrationTest {

    private static final Faker FAKER = new Faker();
    private static final String BUCKET = "test-bucket";

    @Autowired
    private FileFacadeService fileFacadeService;

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private S3Client yandexS3Client;

    private User user;
    private AuthenticatedUser authenticatedUser;
    private MockMultipartFile multipartFile;

    @BeforeEach
    void setUp() throws IOException {
        fileMetadataRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setUsername(FAKER.name().username());
        user.setPassword(FAKER.internet().password());
        user.setRole(UserRole.ADMIN);
        user.setUploadStatus(FileUploadStatus.UNLIMITED);
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);
        authenticatedUser = new AuthenticatedUser(user.getUsername(), user.getRole());

        MDC.put(MdcConstants.REQUEST_ID, UUID.randomUUID().toString());
        MDC.put(MdcConstants.USERNAME, user.getUsername());
        MDC.put(MdcConstants.IP_ADDRESS, "127.0.0.1");

        byte[] imageBytes = Objects.requireNonNull(getClass().getClassLoader()
                .getResourceAsStream("fixtures/java_black.png")).readAllBytes();

        multipartFile = new MockMultipartFile(
                "file",
                "java_black.png",
                "image/png",
                imageBytes
        );

        try {
            yandexS3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            // бакет уже существует — ок
        }
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    /**
     * Загрузка файла: файл в S3, метаданные в БД.
     */
    @Test
    void shouldUploadFile_andSaveMetadata() {
        fileFacadeService.uploadFile(multipartFile, authenticatedUser);

        assertThat(fileMetadataRepository.findAll()).hasSize(1);

        FileMetadata saved = fileMetadataRepository.findAll().getFirst();

        assertThat(saved.getOriginalName()).isEqualTo("java_black.png");
        assertThat(saved.getScanStatus()).isEqualTo(ScanStatus.PENDING_SCAN);
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
    }

    /**
     * Скачивание файла возвращает правильное содержимое.
     */
    @Test
    void shouldDownloadFile_withCorrectContent() throws IOException {
        fileFacadeService.uploadFile(multipartFile, authenticatedUser);

        String uniqueName = fileMetadataRepository.findAll().getFirst().getUniqueName();
        markAsClean(uniqueName);
        var response = fileFacadeService.downloadFile(uniqueName);

        assertThat(response.getContent()).isEqualTo(multipartFile.getBytes());
        assertThat(response.getContentType()).isEqualTo("image/png");
    }

    /**
     * Удаление файла убирает метаданные из БД.
     */
    @Test
    void shouldDeleteFile_andRemoveMetadata() {
        fileFacadeService.uploadFile(multipartFile, authenticatedUser);

        String uniqueName = fileMetadataRepository.findAll().getFirst().getUniqueName();
        fileFacadeService.deleteFile(uniqueName, authenticatedUser);

        assertThat(fileMetadataRepository.findByUniqueName(uniqueName)).isEmpty();
    }

    /**
     * Аудит: после upload, download и delete в БД появляются записи.
     */
    @Test
    void shouldCreateAuditLogs_forUploadDownloadDelete() {
        fileFacadeService.uploadFile(multipartFile, authenticatedUser);

        String uniqueName = fileMetadataRepository.findAll().getFirst().getUniqueName();
        markAsClean(uniqueName);

        fileFacadeService.downloadFile(uniqueName);
        fileFacadeService.deleteFile(uniqueName, authenticatedUser);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(auditLogRepository.count()).isEqualTo(3));
    }

    /**
     * Имитирует успешную антивирусную проверку: в тестах antivirus-service не запущен.
     */
    private void markAsClean(String uniqueName) {
        FileMetadata file = fileMetadataRepository.findByUniqueName(uniqueName).orElseThrow();
        file.setScanStatus(ScanStatus.CLEAN);
        fileMetadataRepository.save(file);
    }
}