package org.resume.s3filemanager.service.auth;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.s3filemanager.audit.AuditOperation;
import org.resume.s3filemanager.audit.Auditable;
import org.resume.s3filemanager.audit.ResourceType;
import org.resume.s3filemanager.constant.SecurityErrorMessages;
import org.resume.s3filemanager.dto.LoginRequest;
import org.resume.s3filemanager.dto.RegisterRequest;
import org.resume.s3filemanager.dto.UserDetailsResponse;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.UserStatus;
import org.resume.s3filemanager.exception.UserAlreadyExistsException;
import org.resume.s3filemanager.exception.UserBlockedException;
import org.resume.s3filemanager.exception.UserNotFoundException;
import org.resume.s3filemanager.mapper.UserMapper;
import org.resume.s3filemanager.security.JwtWhitelistService;
import org.resume.s3filemanager.security.JwtCookieService;
import org.resume.s3filemanager.security.JwtTokenService;
import org.resume.s3filemanager.security.AuthenticatedUser;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Вход, регистрация, выход и данные текущего пользователя.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtWhitelistService jwtWhitelistService;
    private final JwtCookieService jwtCookieService;
    private final UserService userService;
    private final UserMapper userMapper;

    /**
     * Выполняет вход пользователя в систему.
     * <p>
     * Проверяет учетные данные, генерирует JWT токен, сохраняет его
     * в whitelist и устанавливает HTTP-only cookie для клиента.
     *
     * @param request запрос с именем пользователя и паролем
     * @param response HTTP ответ для установки cookie
     * @return данные вошедшего пользователя
     * @throws UserNotFoundException если пользователь не найден
     * @throws UserBlockedException если пользователь заблокирован
     * @throws BadCredentialsException если учетные данные неверны
     */
    public UserDetailsResponse login(LoginRequest request, HttpServletResponse response) {
        User user = verifyCredentials(request.username(), request.password());

        String token = jwtTokenService.generateToken(user.getUsername(), user.getRole());

        jwtWhitelistService.saveToken(
                user.getUsername(),
                token,
                jwtTokenService.getExpirationSeconds()
        );

        jwtCookieService.setAuthCookie(response, token);

        log.info("User logged in successfully: {}", user.getUsername());
        return userMapper.toUserDetailsResponse(user);
    }

    /**
     * Возвращает данные текущего пользователя.
     */
    public UserDetailsResponse getCurrentUser(String username) {
        return userMapper.toUserDetailsResponse(userService.findByUsername(username));
    }

    /**
     * Регистрирует нового пользователя в системе.
     * <p>
     * Создает пользователя с ролью USER и статусом загрузки NOT_UPLOADED.
     *
     * @param request запрос с именем пользователя и паролем
     * @throws UserAlreadyExistsException если пользователь с таким именем уже существует
     */
    @Auditable(operation = AuditOperation.REGISTER, resourceType = ResourceType.USER)
    public void register(RegisterRequest request) {
        userService.createUser(request.username(), request.password());
        log.info("User registered successfully: {}", request.username());
    }

    /**
     * Выполняет выход пользователя из системы.
     * <p>
     * Удаляет JWT токен из whitelist'а и очищает cookie на клиенте.
     *
     * @param authenticatedUser пользователь текущего запроса
     * @param response HTTP ответ для очистки cookie
     */
    public void logout(AuthenticatedUser authenticatedUser, HttpServletResponse response) {
        String username = authenticatedUser.username();

        jwtWhitelistService.deleteToken(username);
        jwtCookieService.clearAuthCookie(response);

        log.info("User logged out successfully: {}", username);
    }

    private User verifyCredentials(String username, String password) {
        User user = userService.findByUsernameForAuth(username);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException(SecurityErrorMessages.INVALID_CREDENTIALS);
        }

        if (user.getStatus() == UserStatus.BLOCKED) {
            log.warn("Blocked user attempted login: {}", username);
            throw new UserBlockedException(username);
        }
        return user;
    }
}
