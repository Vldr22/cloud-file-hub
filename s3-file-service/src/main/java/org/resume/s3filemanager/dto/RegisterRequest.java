package org.resume.s3filemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.resume.s3filemanager.constant.ValidationMessages;

@Schema(description = "Запрос на регистрацию")
public record RegisterRequest(
        @Schema(description = "Имя пользователя", example = "Sara", minLength = 3, maxLength = 50)
        @NotBlank(message = ValidationMessages.FIELD_REQUIRED)
        @Size(min = 3, max = 50, message = ValidationMessages.USERNAME_SIZE)
        String username,

        @Schema(description = "Пароль", example = "secret123", minLength = 8, maxLength = 50)
        @NotBlank(message = ValidationMessages.FIELD_REQUIRED)
        @Size(min = 8, max = 50, message = ValidationMessages.PASSWORD_SIZE)
        String password
) {
}
