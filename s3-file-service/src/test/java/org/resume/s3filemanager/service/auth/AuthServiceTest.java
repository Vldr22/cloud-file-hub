package org.resume.s3filemanager.service.auth;

import com.github.javafaker.Faker;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.s3filemanager.dto.LoginRequest;
import org.resume.s3filemanager.dto.RegisterRequest;
import org.resume.s3filemanager.dto.UserDetailsResponse;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.FileUploadStatus;
import org.resume.s3filemanager.enums.UserRole;
import org.resume.s3filemanager.enums.UserStatus;
import org.resume.s3filemanager.exception.UserAlreadyExistsException;
import org.resume.s3filemanager.exception.UserBlockedException;
import org.resume.s3filemanager.mapper.UserMapper;
import org.resume.s3filemanager.mapper.UserMapperImpl;
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

    @Spy
    private UserMapper userMapper = new UserMapperImpl();

    @InjectMocks
    private AuthService authService;

    private User user;
    private LoginRequest loginRequest;
    private RegisterRequest registerRequest;
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

        loginRequest = new LoginRequest(username, password);
        registerRequest = new RegisterRequest(username, password);
    }

    /**
     * Успешный вход: токен в whitelist и cookie, в ответе данные пользователя.
     */
    @Test
    void shouldLogin_whenCredentialsAreValid() {
        when(userService.findByUsernameForAuth(loginRequest.username())).thenReturn(user);
        when(passwordEncoder.matches(loginRequest.password(), user.getPassword())).thenReturn(true);
        when(jwtTokenService.generateToken(user.getUsername(), user.getRole())).thenReturn(token);
        when(jwtTokenService.getExpirationSeconds()).thenReturn(3600L);

        UserDetailsResponse response = authService.login(loginRequest, httpServletResponse);

        assertThat(response.username()).isEqualTo(user.getUsername());
        assertThat(response.role()).isEqualTo(UserRole.USER);

        verify(jwtWhitelistService).saveToken(eq(user.getUsername()), eq(token), eq(3600L));
        verify(jwtCookieService).setAuthCookie(eq(httpServletResponse), eq(token));
    }

    /**
     * Текущий пользователь находится по имени и возвращается как DTO.
     */
    @Test
    void shouldReturnCurrentUser_whenUserExists() {
        when(userService.findByUsername(user.getUsername())).thenReturn(user);

        UserDetailsResponse response = authService.getCurrentUser(user.getUsername());

        assertThat(response.username()).isEqualTo(user.getUsername());
        assertThat(response.uploadStatus()).isEqualTo(FileUploadStatus.NOT_UPLOADED);
    }

    /**
     * Пользователь заблокирован: UserBlockedException после проверки пароля.
     */
    @Test
    void shouldThrowUserBlockedException_whenUserIsBlocked() {
        user.setStatus(UserStatus.BLOCKED);
        when(userService.findByUsernameForAuth(loginRequest.username())).thenReturn(user);
        when(passwordEncoder.matches(loginRequest.password(), user.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest, httpServletResponse))
                .isInstanceOf(UserBlockedException.class);

        verifyNoInteractions(jwtTokenService, jwtWhitelistService, jwtCookieService);
    }

    /**
     * Неверный пароль: BadCredentialsException.
     */
    @Test
    void shouldThrowBadCredentialsException_whenPasswordIsWrong() {
        when(userService.findByUsernameForAuth(loginRequest.username())).thenReturn(user);
        when(passwordEncoder.matches(loginRequest.password(), user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest, httpServletResponse))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(jwtTokenService, jwtWhitelistService, jwtCookieService);
    }

    /**
     * Успешная регистрация делегирует создание пользователя в UserService.
     */
    @Test
    void shouldRegister_whenUsernameIsUnique() {
        authService.register(registerRequest);

        verify(userService).createUser(registerRequest.username(), registerRequest.password());
    }

    /**
     * Регистрация с занятым именем: UserAlreadyExistsException.
     */
    @Test
    void shouldThrowUserAlreadyExistsException_whenUsernameIsTaken() {
        doThrow(new UserAlreadyExistsException()).when(userService).createUser(anyString(), anyString());

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(UserAlreadyExistsException.class);
    }
}