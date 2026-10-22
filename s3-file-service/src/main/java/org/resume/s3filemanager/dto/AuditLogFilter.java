package org.resume.s3filemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.resume.s3filemanager.audit.AuditOperation;
import org.resume.s3filemanager.enums.CommonResponseStatus;
import org.resume.s3filemanager.validation.ValidDateRange;

import java.time.Instant;

@ValidDateRange
@Schema(description = "Фильтр для получения аудит-логов")
public record AuditLogFilter(
        @Schema(description = "Имя пользователя", example = "Sara")
        String username,

        @Schema(description = "Тип операции", example = "FILE_UPLOAD")
        AuditOperation operation,

        @Schema(description = "Статус операции", example = "SUCCESS")
        CommonResponseStatus status,

        @Schema(description = "Начало периода", example = "2026-10-01T00:00:00Z")
        Instant from,

        @Schema(description = "Конец периода", example = "2026-10-31T23:59:59Z")
        Instant to
) {
}
