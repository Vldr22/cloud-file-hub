package org.resume.s3filemanager.service.file;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Генерирует уникальные ключи объектов в S3-хранилище.
 * Ключ строится из UUID и сохраняет расширение исходного файла,
 * при его отсутствии подставляется расширение по умолчанию.
 */
@Component
public class S3KeyGenerator {

    private static final String DEFAULT_EXTENSION = "tmp";
    private static final String EXTENSION_SEPARATOR = ".";

    public String generate(String originalFilename) {
        return UUID.randomUUID() + EXTENSION_SEPARATOR + resolveExtension(originalFilename);
    }

    private String resolveExtension(String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        return StringUtils.hasText(extension) ? extension : DEFAULT_EXTENSION;
    }
}
