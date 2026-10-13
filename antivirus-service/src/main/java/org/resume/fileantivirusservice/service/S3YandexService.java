package org.resume.fileantivirusservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.fileantivirusservice.constant.ErrorMessages;
import org.resume.fileantivirusservice.exception.S3DownloadException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.resume.common.properties.YandexStorageProperties;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(YandexStorageProperties.class)
public class S3YandexService {

    private final S3Client yandexS3Client;
    private final YandexStorageProperties yandexStorageProperties;

    /**
     * Скачивает файл из S3 по ключу.
     *
     * @param s3Key уникальный ключ файла в S3
     * @return открытый InputStream с содержимым файла, закрывает вызывающий
     * @throws S3DownloadException если произошла техническая ошибка
     */
    public InputStream downloadFile(String s3Key) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(yandexStorageProperties.getBucketName())
                    .key(s3Key)
                    .build();

            return yandexS3Client.getObject(getObjectRequest);

        } catch (S3Exception e) {
            log.error("S3 error downloading file: {}", s3Key, e);
            throw new S3DownloadException(ErrorMessages.S3_SERVICE_UNAVAILABLE, e);
        }
    }
}
