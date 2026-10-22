package org.resume.s3filemanager.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.resume.s3filemanager.constant.ApiPaths;
import org.resume.s3filemanager.dto.CommonResponse;
import org.resume.s3filemanager.dto.FileResponse;
import org.resume.s3filemanager.dto.PageResponse;
import org.resume.s3filemanager.dto.UserDetailsResponse;
import org.resume.s3filemanager.security.AuthenticatedUser;
import org.resume.s3filemanager.service.auth.AuthService;
import org.resume.s3filemanager.service.file.FilePaginationService;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST контроллер текущего пользователя.
 */
@RestController
@RequestMapping(ApiPaths.USERS)
@RequiredArgsConstructor
@Tag(name = "Users", description = "Данные и файлы текущего пользователя")
public class UserController {

    private final AuthService authService;
    private final FilePaginationService filePaginationService;

    @Operation(summary = "Текущий пользователь", description = "Возвращает имя, роль и статус загрузки по JWT из cookie")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешно"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк")
    })
    @GetMapping("/me")
    public CommonResponse<UserDetailsResponse> getCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return CommonResponse.success(authService.getCurrentUser(authenticatedUser.username()));
    }

    @Operation(summary = "Мои файлы", description = "Файлы текущего пользователя с любым статусом антивирусной проверки")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешно"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк")
    })
    @Parameter(name = "page", description = "Номер страницы", example = "0")
    @Parameter(name = "size", description = "Размер страницы", example = "20")
    @GetMapping("/me/files")
    public CommonResponse<PageResponse<FileResponse>> getCurrentUserFiles(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Parameter(hidden = true) Pageable pageable) {
        return CommonResponse.success(PageResponse.from(
                filePaginationService.findUserFiles(authenticatedUser.username(), pageable)));
    }
}
