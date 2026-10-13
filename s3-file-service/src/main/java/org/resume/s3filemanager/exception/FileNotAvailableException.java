package org.resume.s3filemanager.exception;

import org.resume.common.model.ScanStatus;
import org.resume.s3filemanager.constant.ErrorMessages;

public class FileNotAvailableException extends RuntimeException {
    public FileNotAvailableException(ScanStatus status) {
        super(String.format(ErrorMessages.FILE_NOT_AVAILABLE, status));
    }
}
