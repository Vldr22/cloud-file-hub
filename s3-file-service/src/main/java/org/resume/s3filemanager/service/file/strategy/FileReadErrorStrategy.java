package org.resume.s3filemanager.service.file.strategy;

import lombok.extern.slf4j.Slf4j;
import org.resume.s3filemanager.constant.ErrorMessages;
import org.resume.s3filemanager.exception.FileReadException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FileReadErrorStrategy implements ErrorResponseStrategy {

    @Override
    public String handle(String originalFileName, Exception e) {
        log.error("File read error: {}", originalFileName, e);
        return ErrorMessages.FILE_READ_ERROR;
    }

    @Override
    public boolean isSupport(Exception e) {
        return e instanceof FileReadException;
    }

}
