package org.resume.s3filemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Страница результатов")
public record PageResponse<T>(
        @Schema(description = "Элементы страницы")
        List<T> content,

        @Schema(description = "Номер страницы, с 0", example = "0")
        int page,

        @Schema(description = "Размер страницы", example = "20")
        int size,

        @Schema(description = "Всего элементов", example = "42")
        long totalElements,

        @Schema(description = "Всего страниц", example = "3")
        int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
