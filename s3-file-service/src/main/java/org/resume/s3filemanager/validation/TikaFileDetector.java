package org.resume.s3filemanager.validation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Детектор реального типа файлов на основе Apache Tika.
 * <p>
 * Анализирует магические байты (file signature) для определения
 * фактического типа файла, защищая от подмены через переименование.
 *
 * @see Tika
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TikaFileDetector {

    private final Tika tika;

    /**
     * Определяет реальный MIME-тип файла по его содержимому.
     *
     * @param fileBytes содержимое файла
     * @param fileName имя файла (используется как подсказка для Tika)
     * @return определенный MIME-тип без параметров
     */
    public String detectContentType(byte[] fileBytes, String fileName) {
        String detectedType = normalizeContentType(tika.detect(fileBytes, fileName));
        log.debug("Detected content type: {} for file: {}", detectedType, fileName);
        return detectedType;
    }

    private String normalizeContentType(String contentType) {
        MediaType mediaType = MediaType.parseMediaType(contentType);
        return String.format("%s/%s", mediaType.getType(), mediaType.getSubtype());
    }
}
