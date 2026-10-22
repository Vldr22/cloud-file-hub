package org.resume.s3filemanager.service.auth;

import com.github.javafaker.Faker;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.resume.s3filemanager.BaseIntegrationTest;
import org.resume.s3filemanager.dto.LoginRequest;
import org.resume.s3filemanager.dto.RegisterRequest;
import org.resume.s3filemanager.dto.UserDetailsResponse;
import org.resume.s3filemanager.entity.User;
import org.resume.s3filemanager.enums.FileUploadStatus;
import org.resume.s3filemanager.enums.UserRole;
import org.resume.s3filemanager.enums.UserStatus;
import org.resume.s3filemanager.exception.UserAlreadyExistsException;
import org.resume.s3filemanager.exception.UserBlockedException;
import org.resume.s3filemanager.repository.UserRepository;
import org.resume.s3filemanager.security.AuthenticatedUser;
import org.resume.s3filemanager.security.JwtCookieService;
import org.resume.s3filemanager.security.JwtWhitelistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@DisplayName("AuthService IT — аутентификация с реальным Postgres и Redis")
class AuthServiceIT extends BaseIntegrationTest {

    private static final Faker FAKER = new Faker();

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtWhitelistService jwtWhitelistService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JwtCookieService jwtCookieService;

    private String username;
    private String password;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        username = FAKER.name().username();
        password = FAKER.internet().password(8, 16);
        response = new MockHttpServletResponse();

        userRepository.deleteAll();
    }

    // REGISTER

    /**
     * Успешная регистрация: пользователь в Postgres с ролью USER и статусом NOT_UPLOADED.
     */
    @Test
    void shouldRegister_andPersistUserInDatabase() {
        RegisterRequest request = new RegisterRequest(username, password);

        authService.register(request);

        User saved = userRepository.findByUsername(username).orElseThrow();

        assertThat(saved.getUsername()).isEqualTo(username);
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(saved.getUploadStatus()).isEqualTo(FileUploadStatus.NOT_UPLOADED);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(passwordEncoder.matches(password, saved.getPassword())).isTrue();
    }

    /**
     * Повторная регистрация с тем же именем: UserAlreadyExistsException, второй записи в БД нет.
     */
    @Test
    void shouldThrowUserAlreadyExistsException_whenUsernameIsTaken() {
        RegisterRequest request = new RegisterRequest(username, password);
        authService.register(request);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(UserAlreadyExistsException.class);

        assertThat(userRepository.findAll()).hasSize(1);
    }

    // LOGIN
    /**
     * Успешный вход: токен в Redis whitelist, в ответе username и роль.
     */
    @Test
    void shouldLogin_andSaveTokenInRedis() {
        createActiveUser();
        LoginRequest request = new LoginRequest(username, password);

        UserDetailsResponse loginResponse = authService.login(request, response);
        String token = captureCookieToken(response);

        assertThat(loginResponse.username()).isEqualTo(username);
        assertThat(loginResponse.role()).isEqualTo(UserRole.USER);
        assertThat(token).isNotBlank();
        assertThat(jwtWhitelistService.isValid(username, token)).isTrue();
    }

    /**
     * Вход заблокированного пользователя: UserBlockedException, токена в Redis нет.
     */
    @Test
    void shouldThrowUserBlockedException_whenUserIsBlocked() {
        createBlockedUser();
        LoginRequest request = new LoginRequest(username, password);

        assertThatThrownBy(() -> authService.login(request, response))
                .isInstanceOf(UserBlockedException.class);

        assertThat(jwtWhitelistService.isValid(username, "any-token")).isFalse();
    }

    /**
     * Неверный пароль: BadCredentialsException, токена в Redis нет.
     */
    @Test
    void shouldThrowBadCredentialsException_whenPasswordIsWrong() {
        createActiveUser();
        LoginRequest request = new LoginRequest(username, "wrong-password");

        assertThatThrownBy(() -> authService.login(request, response))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(jwtWhitelistService.isValid(username, "any-token")).isFalse();
    }

    // LOGOUT
    /**
     * Успешный выход удаляет токен из Redis whitelist.
     */
    @Test
    void shouldLogout_andRemoveTokenFromRedis() {
        createActiveUser();
        authService.login(new LoginRequest(username, password), response);
        String token = captureCookieToken(response);

        authService.logout(new AuthenticatedUser(username, UserRole.USER), response);

        assertThat(jwtWhitelistService.isValid(username, token)).isFalse();

        verify(jwtCookieService).clearAuthCookie(response);
    }

    /**
     * Повторный вход после выхода: новый токен валиден, старый инвалидирован.
     */
    @Test
    void shouldIssueNewToken_afterLogoutAndLoginAgain() {
        createActiveUser();
        LoginRequest request = new LoginRequest(username, password);

        authService.login(request, response);
        String firstToken = captureCookieToken(response);
        authService.logout(new AuthenticatedUser(username, UserRole.USER), response);

        assertThat(jwtWhitelistService.isValid(username, firstToken)).isFalse();

        HttpServletResponse secondResponse = new MockHttpServletResponse();
        authService.login(request, secondResponse);
        String secondToken = captureCookieToken(secondResponse);

        assertThat(jwtWhitelistService.isValid(username, secondToken)).isTrue();
    }

    // HELPERS
    private String captureCookieToken(HttpServletResponse response) {
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(jwtCookieService).setAuthCookie(eq(response), tokenCaptor.capture());
        return tokenCaptor.getValue();
    }

    private void createActiveUser() {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(UserRole.USER);
        user.setUploadStatus(FileUploadStatus.NOT_UPLOADED);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    private void createBlockedUser() {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(UserRole.USER);
        user.setUploadStatus(FileUploadStatus.NOT_UPLOADED);
        user.setStatus(UserStatus.BLOCKED);
        userRepository.save(user);
    }
}