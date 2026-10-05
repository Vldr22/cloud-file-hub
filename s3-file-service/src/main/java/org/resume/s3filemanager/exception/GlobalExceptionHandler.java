package org.resume.s3filemanager.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.resume.s3filemanager.constant.ErrorMessages;
import org.resume.s3filemanager.constant.SecurityErrorMessages;
import org.resume.s3filemanager.constant.ValidationMessages;
import org.resume.s3filemanager.dto.CommonResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Objects;

/**
 * Глобальный обработчик исключений для REST API.
 * <p>
 * Перехватывает исключения из всех контроллеров и преобразует их
 * в унифицированный формат ответа {@link CommonResponse} с ProblemDetail.
 * <p>
 * Обрабатывает:
 * <ul>
 *   <li>Бизнес-исключения (DuplicateFile, UserNotFound, и т.д.)</li>
 *   <li>Технические исключения (S3, FileRead)</li>
 *   <li>Framework исключения (Validation, Security, Database)</li>
 * </ul>
 *
 * @see CommonResponse
 * @see ProblemDetail
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.servlet.multipart.max-file-size:30MB}")
    private String maxFileSize;
    public static final String ERRORS = "errors";

    // ========== BUSINESS EXCEPTIONS  ==========
    @ExceptionHandler(DuplicateFileException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public CommonResponse<Void> handleDuplicateFile(DuplicateFileException e) {
        return createErrorResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public CommonResponse<Void> handleUserAlreadyExists(UserAlreadyExistsException e) {
        return createErrorResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(FileUploadLimitException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public CommonResponse<Void> handleFileUploadLimit(FileUploadLimitException e) {
        return createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(FileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public CommonResponse<Void> handleFileNotFound() {
        return createErrorResponse(HttpStatus.NOT_FOUND, ErrorMessages.FILE_NOT_FOUND);
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public CommonResponse<Void> handleUserNotFound() {
        return createErrorResponse(HttpStatus.NOT_FOUND, ErrorMessages.USER_NOT_FOUND);
    }

    @ExceptionHandler(FileAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public CommonResponse<Void> handleFileAccessDenied(FileAccessDeniedException e) {
        return createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(MultipleFileUploadException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResponse<Void> handleFileUploadException(MultipleFileUploadException e) {
        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorMessages.FILES_UPLOAD_ERROR,
                e.getResponses()
        );
    }

    @ExceptionHandler(UserBlockedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public CommonResponse<Void> handleUserBlocked(UserBlockedException e) {
        return createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(InvalidScanStatusException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResponse<Void> handleInvalidScanStatus(InvalidScanStatusException e) {
        return createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // ========== TECHNICAL EXCEPTIONS  ==========
    @ExceptionHandler(FileReadException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResponse<Void> handleFileRead() {
        return createErrorResponse(HttpStatus.BAD_REQUEST, ErrorMessages.FILE_READ_ERROR);
    }

    @ExceptionHandler(S3YandexException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public CommonResponse<Void> handleS3Yandex() {
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMessages.FILE_STORAGE_ERROR);
    }

    // ========== FRAMEWORK EXCEPTIONS ==========
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public CommonResponse<Void> handleMaxUploadSize() {
        log.warn("File size exceeds maximum: {}", maxFileSize);
        return createErrorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE,
                String.format(ErrorMessages.FILE_SIZE_EXCEEDED, maxFileSize)
        );
    }

    // === Validation ===
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResponse<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(ValidationMessages.VALIDATION_FAILED);

        log.warn("Validation failed: {}", message);
        return createErrorResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResponse<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations()
                .stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse(ValidationMessages.VALIDATION_FAILED);

        log.warn("Constraint violation: {}", message);
        return createErrorResponse(HttpStatus.BAD_REQUEST, message);
    }

    // === Request ===
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResponse<Void> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("Missing required parameter: {}", e.getParameterName());
        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                String.format(ErrorMessages.MISSING_PARAMETER, e.getParameterName())
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String requiredType = Objects.requireNonNull(e.getRequiredType()).getSimpleName();

        log.warn("Type mismatch for parameter '{}': expected {}", e.getName(), requiredType);
        return createErrorResponse(
                HttpStatus.BAD_REQUEST,
                String.format(ErrorMessages.INVALID_PARAMETER_TYPE, e.getName(), requiredType)
        );
    }

    // === Database ===
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public CommonResponse<Void> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.error("Data integrity violation: {}", e.getMessage(), e);

        String message = ErrorMessages.DATA_INTEGRITY_GENERIC;
        if (e.getMessage() != null) {
            if (e.getMessage().contains("unique") || e.getMessage().contains("duplicate")) {
                message = ErrorMessages.DATA_INTEGRITY_UNIQUE;
            } else if (e.getMessage().contains("foreign key")) {
                message = ErrorMessages.DATA_INTEGRITY_FK;
            }
        }

        return createErrorResponse(HttpStatus.CONFLICT, message);
    }

    // === S3 ===
    @ExceptionHandler(S3Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public CommonResponse<Void> handleAmazonS3(S3Exception e) {
        log.error("AWS S3 error: {} (status: {})",
                e.awsErrorDetails().errorCode(),
                e.statusCode(),
                e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMessages.FILE_STORAGE_ERROR);
    }

    // === Security ===
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public CommonResponse<Void> handleBadCredentials() {
        log.warn("Authentication failed: invalid credentials");
        return createErrorResponse(HttpStatus.UNAUTHORIZED, SecurityErrorMessages.INVALID_CREDENTIALS);
    }

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public CommonResponse<Void> handleAuthentication(AuthenticationException e) {
        log.warn("Authentication error: {}", e.getMessage());
        return createErrorResponse(HttpStatus.UNAUTHORIZED, ErrorMessages.AUTHENTICATION_REQUIRED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public CommonResponse<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return createErrorResponse(HttpStatus.FORBIDDEN, ErrorMessages.INSUFFICIENT_PERMISSIONS);
    }

    // === General ===
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Invalid argument: {}", e.getMessage(), e);
        return createErrorResponse(HttpStatus.BAD_REQUEST, ErrorMessages.UNEXPECTED_ERROR);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public CommonResponse<Void> handleIllegalState(IllegalStateException e) {
        log.error("Illegal state: {}", e.getMessage(), e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMessages.UNEXPECTED_ERROR);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public CommonResponse<Void> handleGeneral(Exception e) {
        log.error("Unexpected error occurred", e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMessages.UNEXPECTED_ERROR);
    }

    // ========== HELPER ==========
    private CommonResponse<Void> createErrorResponse(HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        return CommonResponse.error(problemDetail);
    }

    private CommonResponse<Void> createErrorResponse(HttpStatus status, String detail, Object extra) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty(ERRORS, extra);
        return CommonResponse.error(problemDetail);
    }
}