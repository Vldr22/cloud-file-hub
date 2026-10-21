package org.resume.s3filemanager.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.resume.s3filemanager.enums.CommonResponseStatus;
import org.springframework.http.ProblemDetail;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Общий формат ответа API")
public record CommonResponse<T>(
        @Schema(description = "Данные ответа")
        T data,

        @Schema(description = "Статус ответа", example = "SUCCESS")
        CommonResponseStatus status,

        @Schema(description = "Детали ошибки (только при ERROR)")
        ProblemDetail problemDetail,

        @Schema(description = "Время ответа", example = "2026-03-11T03:41:17.639342344")
        LocalDateTime timestamp) {

    public static <T> CommonResponse<T> success(T data) {
        return new CommonResponse<>(
                data,
                CommonResponseStatus.SUCCESS,
                null,
                LocalDateTime.now());
    }

    public static <T> CommonResponse<T> error(ProblemDetail problemDetail) {
        return new CommonResponse<>(
                null,
                CommonResponseStatus.ERROR,
                problemDetail,
                LocalDateTime.now());
    }

}
