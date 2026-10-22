package org.resume.s3filemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.resume.s3filemanager.constant.ValidationMessages;

@Schema(description = "Запрос на вход")
public record LoginRequest(
        @Schema(description = "Имя пользователя", example = "Sara", maxLength = 50)
        @NotBlank(message = ValidationMessages.FIELD_REQUIRED)
        @Size(max = 50, message = ValidationMessages.USERNAME_TOO_LONG)
        String username,

        @Schema(description = "Пароль", example = "secret123", maxLength = 50)
        @NotBlank(message = ValidationMessages.FIELD_REQUIRED)
        @Size(max = 50, message = ValidationMessages.PASSWORD_TOO_LONG)
        String password
) {
}
