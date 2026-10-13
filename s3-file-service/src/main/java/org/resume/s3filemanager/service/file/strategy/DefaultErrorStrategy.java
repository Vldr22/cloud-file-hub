package org.resume.s3filemanager.service.file.strategy;

import lombok.extern.slf4j.Slf4j;
import org.resume.s3filemanager.constant.ErrorMessages;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DefaultErrorStrategy implements ErrorResponseStrategy {

    @Override
    public String handle(String originalFileName, Exception e) {
        log.error("Unexpected error uploading file: {}", originalFileName, e);
        return ErrorMessages.UNEXPECTED_ERROR;
    }

    @Override
    public boolean isSupport(Exception e) {
        return false;
    }

}
