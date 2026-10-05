package org.resume.s3filemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.resume.s3filemanager.audit.AuditOperation;
import org.resume.s3filemanager.enums.CommonResponseStatus;

import java.time.Instant;

@Schema(description = "Фильтр для получения аудит-логов")
public record AuditLogFilter(

        @Schema(description = "Имя пользователя", example = "Sara")
        String username,

        @Schema(description = "Тип операции", example = "FILE_UPLOAD")
        AuditOperation operation,

        @Schema(description = "Статус операции", example = "SUCCESS")
        CommonResponseStatus status,

        @Schema(description = "Начало периода", example = "2026-03-11T03:41:17.639342344")
        Instant from,

        @Schema(description = "Начало периода", example = "2026-03-11T03:41:17.639342344")
        Instant to) {

}
