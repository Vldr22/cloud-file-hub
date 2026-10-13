package org.resume.s3filemanager.service.file.strategy;

import lombok.extern.slf4j.Slf4j;
import org.resume.s3filemanager.constant.ErrorMessages;
import org.resume.s3filemanager.exception.S3YandexException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class S3YandexErrorStrategy implements ErrorResponseStrategy {

    @Override
    public String handle(String originalFileName, Exception e) {
        log.error("S3 storage error: {}", originalFileName, e);
        return ErrorMessages.FILE_STORAGE_ERROR;
    }

    @Override
    public boolean isSupport(Exception e) {
        return e instanceof S3YandexException;
    }
}
