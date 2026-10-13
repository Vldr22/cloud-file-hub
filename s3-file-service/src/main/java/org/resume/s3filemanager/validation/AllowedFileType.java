package org.resume.s3filemanager.validation;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * Перечисление разрешенных типов файлов для загрузки.
 * <p>
 * Содержит MIME-типы и расширения файлов, организованные по категориям:
 * документы, изображения, видео, аудио, архивы и текстовые файлы.
 */
@Getter
@RequiredArgsConstructor
public enum AllowedFileType {

    // Документы
    PDF("application/pdf", "pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
    ODT("application/vnd.oasis.opendocument.text", "odt"),
    RTF("application/rtf", "rtf"),

    // Изображения
    JPEG("image/jpeg", "jpg"),
    JPEG_ALT("image/jpeg", "jpeg"),
    PNG("image/png", "png"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp"),
    BMP("image/bmp", "bmp"),
    ICO("image/x-icon", "ico"),

    // Видео
    MP4("video/mp4", "mp4"),
    WEBM("video/webm", "webm"),

    // Аудио
    MP3("audio/mpeg", "mp3"),
    WAV("audio/wav", "wav"),
    OGG("audio/ogg", "ogg"),

    // Архивы
    ZIP("application/zip", "zip"),
    RAR("application/x-rar-compressed", "rar"),
    SEVEN_ZIP("application/x-7z-compressed", "7z"),

    // Текст/Код
    TXT("text/plain", "txt"),
    CSV("text/csv", "csv"),
    JSON("application/json", "json"),
    XML("application/xml", "xml"),
    MARKDOWN("text/markdown", "md");

    private final String contentType;
    private final String extension;

    /**
     * Проверяет, есть ли расширение (без точки) в списке разрешенных.
     */
    public static boolean isExtensionAllowed(String extension) {
        return Arrays.stream(values())
                .anyMatch(type -> type.extension.equalsIgnoreCase(extension));
    }

    /**
     * Проверяет, разрешен ли файл с указанным расширением и MIME-типом.
     * <p>
     * Оба параметра должны совпадать с одной из комбинаций в enum.
     *
     * @param extension расширение файла (без точки)
     * @param contentType MIME-тип файла
     * @return true если комбинация разрешена, false в противном случае
     */
    public static boolean isAllowed(String extension, String contentType) {
        return Arrays.stream(values())
                .anyMatch(type ->
                        type.extension.equalsIgnoreCase(extension) &&
                                type.contentType.equalsIgnoreCase(contentType)
                );
    }
}
