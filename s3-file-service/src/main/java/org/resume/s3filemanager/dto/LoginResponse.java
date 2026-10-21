package org.resume.s3filemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ответ при успешной аутентификации")
public record LoginResponse(
        @Schema(description = "Имя пользователя", example = "Sara")
        String login,

        @Schema(description = "Роль пользователя", example = "ROLE_USER")
        String role) {
}
