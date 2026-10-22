package org.resume.s3filemanager.constant;

import lombok.experimental.UtilityClass;

/**
 * Константы для сообщений связанных с валидацией
 */
@UtilityClass
public class ValidationMessages {

    // Auth
    public static final String USERNAME_SIZE = "Username must be between 3 and 50 characters";
    public static final String PASSWORD_SIZE = "Password must be between 8 and 50 characters";
    public static final String USERNAME_TOO_LONG = "Username must be at most 50 characters";
    public static final String PASSWORD_TOO_LONG = "Password must be at most 50 characters";
    public static final String FIELD_REQUIRED = "This field is required";

    // File validation
    public static final String INVALID_FILE_TYPE = "Invalid file type";
    public static final String INVALID_BATCH_SIZE = "Invalid batch size";
    public static final String FILE_EMPTY = "File is empty or not selected";
    public static final String FILE_TYPE_UNKNOWN = "Unable to determine file type";
    public static final String FILE_TYPE_NOT_ALLOWED = "File type not allowed: %s (%s)";
    public static final String FILE_EXTENSION_NOT_ALLOWED = "File extension not allowed: %s";
    public static final String FILE_PROCESSING_ERROR = "Error processing file validation";
    public static final String MAX_FILES_EXCEEDED = "Maximum %d files allowed per upload";

    // Period
    public static final String INVALID_DATE_RANGE = "Start of the period must not be after its end";

    // Generic validation
    public static final String VALIDATION_FAILED = "Validation failed";
}
