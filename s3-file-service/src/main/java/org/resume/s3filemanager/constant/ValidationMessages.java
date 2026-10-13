package org.resume.s3filemanager.constant;

import lombok.experimental.UtilityClass;

/**
 * Константы для сообщений связанных с валидацией
 */
@UtilityClass
public class ValidationMessages {

    // Auth
    public static final String USERNAME_SIZE = "Username must be between 3 and 50 characters";
    public static final String PASSWORD_SIZE = "Password must be between 3 and 50 characters";
    public static final String FIELD_REQUIRED = "This field is required";

    // File validation
    public static final String INVALID_FILE_TYPE = "Invalid file type";
    public static final String INVALID_BATCH_SIZE = "Invalid batch size";
    public static final String FILE_EMPTY = "File is empty or not selected";
    public static final String FILE_TYPE_UNKNOWN = "Unable to determine file type";
    public static final String FILE_TYPE_NOT_ALLOWED = "File type not allowed: %s (%s)";
    public static final String FILE_SIGNATURE_MISMATCH = "File does not match declared type: %s";
    public static final String FILE_PROCESSING_ERROR = "Error processing file validation";
    public static final String MAX_FILES_EXCEEDED = "Maximum %d files allowed per upload";

    // Generic validation
    public static final String VALIDATION_FAILED = "Validation failed";
}
