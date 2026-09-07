package org.resume.s3filemanager.service.file.strategy;

import lombok.extern.slf4j.Slf4j;
import org.resume.s3filemanager.constant.ErrorMessages;
import org.resume.s3filemanager.exception.DuplicateFileException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DuplicateFileErrorStrategy implements ErrorResponseStrategy{

    @Override
    public String handle(String originalFileName, Exception e) {
        log.warn("Duplicate file: {}", originalFileName);
        return ErrorMessages.FILE_ALREADY_BEEN_UPLOADED;
    }

    @Override
    public boolean isSupport(Exception e) {
        return e instanceof DuplicateFileException;
    }
}
