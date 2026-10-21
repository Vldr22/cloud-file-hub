package org.resume.s3filemanager.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.resume.s3filemanager.constant.ApiPaths;
import org.resume.s3filemanager.dto.CommonResponse;
import org.resume.s3filemanager.dto.UserDetailsResponse;
import org.resume.s3filemanager.service.auth.AuthService;
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
@Tag(name = "Users", description = "Данные текущего пользователя")
public class UserController {

    private final AuthService authService;

    @Operation(summary = "Текущий пользователь", description = "Возвращает имя, роль и статус загрузки по JWT из cookie")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешно"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк")
    })
    @GetMapping("/me")
    public CommonResponse<UserDetailsResponse> getCurrentUser(@AuthenticationPrincipal String username) {
        return CommonResponse.success(authService.getCurrentUser(username));
    }
}
