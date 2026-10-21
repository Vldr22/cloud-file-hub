package org.resume.s3filemanager.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.resume.s3filemanager.constant.ApiPaths;
import org.resume.s3filemanager.constant.SuccessMessages;
import org.resume.s3filemanager.dto.AuthRequest;
import org.resume.s3filemanager.dto.CommonResponse;
import org.resume.s3filemanager.dto.UserDetailsResponse;
import org.resume.s3filemanager.service.auth.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST контроллер для аутентификации и авторизации пользователей.
 * <p>
 * Обрабатывает регистрацию, вход и выход пользователей из системы.
 * JWT токены передаются через HTTP-only cookie.
 *
 * @see AuthService
 */
@RestController
@RequestMapping(ApiPaths.AUTH)
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Регистрация, вход и выход из системы")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Войти в систему", description = "Проверяет данные и устанавливает JWT Token в cookie")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешный вход"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "401", description = "Неверные учётные данные")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public CommonResponse<UserDetailsResponse> login(@Valid @RequestBody AuthRequest request, HttpServletResponse response) {
        return CommonResponse.success(authService.login(request, response));
    }

    @Operation(summary = "Регистрация", description = "Создаёт нового пользователя с ролью USER")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Пользователь создан"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации"),
            @ApiResponse(responseCode = "409", description = "Пользователь с таким именем уже зарегистрирован")
    })
    @SecurityRequirements
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CommonResponse<String> register(@Valid @RequestBody AuthRequest request) {
        authService.register(request);
        return CommonResponse.success(SuccessMessages.REGISTRATION_SUCCESS);
    }

    @Operation(summary = "Выйти из системы", description = "Удаляет токен из whitelist и очищает cookie")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешный выход"),
            @ApiResponse(responseCode = "401", description = "Токен отсутствует или истёк")
    })
    @PostMapping("/logout")
    public CommonResponse<String> logout(HttpServletResponse response) {
        authService.logout(response);
        return CommonResponse.success(SuccessMessages.LOGOUT_SUCCESS);
    }
}
