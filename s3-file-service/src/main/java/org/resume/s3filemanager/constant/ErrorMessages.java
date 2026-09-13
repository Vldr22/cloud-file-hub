package org.resume.s3filemanager.constant;

import lombok.experimental.UtilityClass;

/**
 * Константы сообщений об ошибках для исключений и HTTP ответов.
 */
@UtilityClass
public class ErrorMessages {

    // User
    public static final String USER_NOT_FOUND = "User not found";
    public static final String USER_ALREADY_EXISTS = "User already exists";
    public static final String USER_BLOCKED = "User account is blocked!";

    // File
    public static final String FILE_NOT_FOUND = "File not found";
    public static final String DUPLICATE_FILE_ERROR = "Duplicate file. File already exists";
    public static final String FILE_STORAGE_ERROR = "Unable to process the file operation in the storage";
    public static final String FILE_ALREADY_BEEN_UPLOADED = "File already been uploaded";
    public static final String FILE_SIZE_EXCEEDED = "File size exceeds the maximum allowed limit of %s";
    public static final String FILE_READ_ERROR = "Failed to read file";
    public static final String ACCESS_DENIED_DELETE_FILE = "Cannot delete other user's file";
    public static final String FILES_UPLOAD_ERROR = "Failed to upload all added files";

    // Database
    public static final String DATA_INTEGRITY_UNIQUE = "Record with this data already exists";
    public static final String DATA_INTEGRITY_FK = "Cannot perform operation - related records exist";
    public static final String DATA_INTEGRITY_GENERIC = "Data integrity violation";

    // HTTP
    public static final String MISSING_PARAMETER = "Required parameter '%s' is missing";
    public static final String INVALID_PARAMETER_TYPE = "Parameter '%s' must be of type %s";

    // General
    public static final String INSUFFICIENT_PERMISSIONS = "Insufficient permissions";
    public static final String AUTHENTICATION_REQUIRED = "Authentication required";
    public static final String UNEXPECTED_ERROR = "An unexpected error occurred. Please try again later";

    // Kafka
    public static final String DLT_RETRY_FAILED =  "Retry доступен только для файлов со статусом ERROR, current status: %s";
}
