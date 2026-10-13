package org.resume.s3filemanager.service.auth;

import com.github.javafaker.Faker;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.s3filemanager.dto.AuthRequest;
import org.resume.s3filemanager.dto.LoginResponse;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.FileUploadStatus;
import org.resume.s3filemanager.enums.UserRole;
import org.resume.s3filemanager.enums.UserStatus;
import org.resume.s3filemanager.exception.UserAlreadyExistsException;
import org.resume.s3filemanager.exception.UserBlockedException;
import org.resume.s3filemanager.security.JwtCookieService;
import org.resume.s3filemanager.security.JwtTokenService;
import org.resume.s3filemanager.security.JwtWhitelistService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — аутентификация и регистрация пользователей")
class AuthServiceTest {

    private static final Faker FAKER = new Faker();

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private JwtWhitelistService jwtWhitelistService;

    @Mock
    private JwtCookieService jwtCookieService;

    @Mock
    private UserService userService;

    @Mock
    private HttpServletResponse httpServletResponse;

    @InjectMocks
    private AuthService authService;

    private User user;
    private AuthRequest authRequest;
    private String token;

    @BeforeEach
    void setUp() {
        String username = FAKER.name().username();
        String password = FAKER.internet().password();
        token = FAKER.internet().uuid();

        user = new User();
        user.setId(FAKER.number().randomNumber());
        user.setUsername(username);
        user.setPassword(password);
        user.setRole(UserRole.USER);
        user.setUploadStatus(FileUploadStatus.NOT_UPLOADED);
        user.setStatus(UserStatus.ACTIVE);

        authRequest = new AuthRequest(username, password);
    }

    /**
     * Успешный вход — токен генерируется, whitelist обновляется, cookie устанавливается.
     */
    @Test
    void shouldLogin_whenCredentialsAreValid() {
        when(userService.findByUsernameForAuth(authRequest.username())).thenReturn(user);
        when(passwordEncoder.matches(authRequest.password(), user.getPassword())).thenReturn(true);
        when(jwtTokenService.generateToken(user.getUsername(), user.getRole())).thenReturn(token);
        when(jwtTokenService.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(authRequest, httpServletResponse);

        assertThat(response.token()).isEqualTo(token);
        assertThat(response.login()).isEqualTo(user.getUsername());
        assertThat(response.role()).isEqualTo(UserRole.USER.getAuthority());

        verify(jwtWhitelistService).saveToken(eq(user.getUsername()), eq(token), eq(3600L));
        verify(jwtCookieService).setAuthCookie(eq(httpServletResponse), eq(token));
    }

    /**
     * Пользователь заблокирован - UserBlockedException после проверки пароля.
     */
    @Test
    void shouldThrowUserBlockedException_whenUserIsBlocked() {
        user.setStatus(UserStatus.BLOCKED);
        when(userService.findByUsernameForAuth(authRequest.username())).thenReturn(user);
        when(passwordEncoder.matches(authRequest.password(), user.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(authRequest, httpServletResponse))
                .isInstanceOf(UserBlockedException.class);

        verifyNoInteractions(jwtTokenService, jwtWhitelistService, jwtCookieService);
    }

    /**
     * Неверный пароль — BadCredentialsException.
     */
    @Test
    void shouldThrowBadCredentialsException_whenPasswordIsWrong() {
        when(userService.findByUsernameForAuth(authRequest.username())).thenReturn(user);
        when(passwordEncoder.matches(authRequest.password(), user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(authRequest, httpServletResponse))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(jwtTokenService, jwtWhitelistService, jwtCookieService);
    }

    /**
     * Успешная регистрация — делегирует создание пользователя в UserService.
     */
    @Test
    void shouldRegister_whenUsernameIsUnique() {
        authService.register(authRequest);

        verify(userService).createUser(authRequest.username(), authRequest.password());
    }

    /**
     * Регистрация с занятым именем — UserAlreadyExistsException.
     */
    @Test
    void shouldThrowUserAlreadyExistsException_whenUsernameIsTaken() {
        doThrow(new UserAlreadyExistsException()).when(userService).createUser(anyString(), anyString());

        assertThatThrownBy(() -> authService.register(authRequest))
                .isInstanceOf(UserAlreadyExistsException.class);
    }
}