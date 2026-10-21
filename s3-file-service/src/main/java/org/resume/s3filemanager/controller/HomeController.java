package org.resume.s3filemanager.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.resume.s3filemanager.constant.ApiPaths;
import org.resume.s3filemanager.dto.FileResponse;
import org.resume.s3filemanager.service.file.FilePaginationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST контроллер для публичного доступа к списку файлов.
 * <p>
 * Предоставляет постраничный список всех файлов в системе
 * без требования аутентификации.
 *
 * @see FilePaginationService
 */
@RestController
@RequestMapping(ApiPaths.HOME)
@RequiredArgsConstructor
@Tag(name = "Home", description = "Домашняя страница. Публичный доступ к списку файлов")
public class HomeController {

    private final FilePaginationService filePaginationService;

    @Operation(summary = "Список файлов", description = "Постраничный список всех файлов без аутентификации")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешно"),
            @ApiResponse(responseCode = "400", description = "Неверные параметры пагинации")
    })
    @Parameter(name = "page", description = "Номер страницы", example = "0")
    @Parameter(name = "size", description = "Размер страницы", example = "20")
    @SecurityRequirements
    @GetMapping()
    public Page<FileResponse> getFiles(@Parameter(hidden = true) Pageable pageable) {
        return filePaginationService.paginate(pageable);
    }
}
