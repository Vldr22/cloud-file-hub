package org.resume.s3filemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import org.resume.common.model.ScanStatus;

import java.time.Instant;

@Builder
@Schema(description = "Расширенная информация о файле для администратора")
public record AdminFileResponse(
        @Schema(description = "ID файла", example = "1")
        Long id,

        @Schema(description = "Оригинальное имя файла", example = "photo.png")
        String fileName,

        @Schema(description = "Уникальное имя файла в хранилище", example = "212d7ce9-8451-4c4d-881e-d198593aa518.png")
        String uniqueName,

        @Schema(description = "Размер файла в байтах", example = "1080033")
        long fileSize,

        @Schema(description = "MIME тип файла", example = "image/png")
        String type,

        @Schema(description = "Статус антивирусной проверки", example = "CLEAN")
        ScanStatus scanStatus,

        @Schema(description = "Имя пользователя загрузившего файл", example = "Sara")
        String uploadedUserName,

        @Schema(description = "Время загрузки", example = "2026-10-22T10:15:30Z")
        Instant uploadedAt
) {
}
