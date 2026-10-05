package org.resume.s3filemanager.service.file;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.resume.s3filemanager.BaseIntegrationTest;
import org.resume.s3filemanager.exception.FileNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YandexStorageServiceIT extends BaseIntegrationTest {

    private static final Faker FAKER = new Faker();
    private static final String BUCKET = "test-bucket";
    private static final String ORIGINAL_FILENAME = "document.txt";

    @Autowired
    private YandexStorageService storageService;

    @Autowired
    private S3KeyGenerator s3KeyGenerator;

    @Autowired
    private S3Client yandexS3Client;

    private String fileName;
    private byte[] content;
    private String contentType;

    @BeforeEach
    void setUp() {
        fileName = s3KeyGenerator.generate(ORIGINAL_FILENAME);
        content = FAKER.lorem().sentence().getBytes();
        contentType = "text/plain";

        try {
            yandexS3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
            // бакет уже существует — ок
        }
    }

    /**
     * Проверяет загрузку реального файла в хранилище и его последующее скачивание.
     * <p>
     * Ожидается что бинарное содержимое скачанного файла совпадает с оригиналом.
     */
    @Test
    void shouldUploadAndDownloadRealFile() throws IOException {
        byte[] imageBytes = Objects.requireNonNull(
                getClass().getResourceAsStream("/fixtures/java_black.png")).readAllBytes();

        storageService.uploadFileYandexS3(fileName, imageBytes, "image/png");

        byte[] downloaded = storageService.downloadFileYandexS3(fileName);
        assertThat(downloaded).isEqualTo(imageBytes);
    }

    /**
     * Проверяет загрузку файла в хранилище и его последующее скачивание.
     * <p>
     * Ожидается что содержимое скачанного файла совпадает с загруженным.
     */
    @Test
    void shouldUploadAndDownloadFile() {
        storageService.uploadFileYandexS3(fileName, content, contentType);

        byte[] downloaded = storageService.downloadFileYandexS3(fileName);

        assertThat(downloaded).isEqualTo(content);
    }

    /**
     * Проверяет удаление файла из хранилища.
     * После удаления попытка скачать файл вызывает {@link FileNotFoundException}.
     */
    @Test
    void shouldDeleteFile() {
        storageService.uploadFileYandexS3(fileName, content, contentType);
        storageService.deleteFileYandexS3(fileName);

        assertThatThrownBy(() -> storageService.downloadFileYandexS3(fileName))
                .isInstanceOf(FileNotFoundException.class);
    }
}