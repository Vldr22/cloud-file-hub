package org.resume.s3filemanager.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.s3filemanager.constant.ApiPaths;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenFilterTest {

    private static final String TOKEN = "test.jwt.token";
    private static final String USERNAME = "john";
    private static final String ROLE = "ROLE_CLIENT";
    private static final String PROTECTED_PATH = ApiPaths.FILES;
    private static final String PUBLIC_PATH = ApiPaths.AUTH + "/login";

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private JwtWhitelistService jwtWhitelistService;

    @Mock
    private JwtCookieService jwtCookieService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private JwtTokenFilter jwtTokenFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Токен отсутствует в cookie — запрос пропускается дальше по цепочке.
     */
    @Test
    void doFilterInternal_continuesChain_whenTokenIsNull() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtCookieService.extractToken(request)).thenReturn(null);

        jwtTokenFilter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(jwtTokenService, jwtWhitelistService);
    }

    /**
     * Токен не прошёл проверку whitelist — возвращается 401.
     */
    @Test
    void doFilterInternal_returns401_whenTokenNotInWhitelist() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtCookieService.extractToken(request)).thenReturn(TOKEN);
        when(jwtTokenService.extractSubject(TOKEN)).thenReturn(USERNAME);
        when(jwtWhitelistService.isValid(USERNAME, TOKEN)).thenReturn(false);

        jwtTokenFilter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    /**
     * Токен истёк — возвращается 401 с сообщением TOKEN_EXPIRED.
     */
    @Test
    void doFilterInternal_returns401_whenTokenExpired() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtCookieService.extractToken(request)).thenReturn(TOKEN);
        when(jwtTokenService.extractSubject(TOKEN)).thenThrow(mock(ExpiredJwtException.class));

        jwtTokenFilter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(chain.getRequest()).isNull();
    }

    /**
     * Токен невалиден (общее исключение) — возвращается 401.
     */
    @Test
    void doFilterInternal_returns401_whenTokenInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtCookieService.extractToken(request)).thenReturn(TOKEN);
        when(jwtTokenService.extractSubject(TOKEN)).thenThrow(new RuntimeException());

        jwtTokenFilter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    /**
     * Токен валиден — аутентификация устанавливается в SecurityContext, запрос пропускается.
     */
    @Test
    void doFilterInternal_setsAuthentication_whenTokenValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtCookieService.extractToken(request)).thenReturn(TOKEN);
        when(jwtTokenService.extractSubject(TOKEN)).thenReturn(USERNAME);
        when(jwtWhitelistService.isValid(USERNAME, TOKEN)).thenReturn(true);
        when(jwtTokenService.extractRole(TOKEN)).thenReturn(ROLE);

        jwtTokenFilter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(USERNAME);
        assertThat(chain.getRequest()).isNotNull();
    }

    /**
     * Публичный путь — фильтр пропускает запрос без проверки токена.
     */
    @Test
    void shouldNotFilter_returnsTrue_forPublicPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PUBLIC_PATH);

        assertThat(jwtTokenFilter.shouldNotFilter(request)).isTrue();
    }

    /**
     * Защищённый путь — фильтр применяется.
     */
    @Test
    void shouldNotFilter_returnsFalse_forProtectedPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_PATH);

        assertThat(jwtTokenFilter.shouldNotFilter(request)).isFalse();
    }
}