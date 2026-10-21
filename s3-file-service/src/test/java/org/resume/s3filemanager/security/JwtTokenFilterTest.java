package org.resume.s3filemanager.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.s3filemanager.constant.ApiPaths;
import org.resume.s3filemanager.constant.SecurityErrorMessages;
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
    private SecurityExceptionHandler securityExceptionHandler;

    @InjectMocks
    private JwtTokenFilter jwtTokenFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Без токена в cookie запрос идёт дальше по цепочке.
     */
    @Test
    void doFilterInternal_continuesChain_whenTokenIsNull() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtCookieService.extractToken(request)).thenReturn(null);

        jwtTokenFilter.doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(jwtTokenService, jwtWhitelistService, securityExceptionHandler);
    }

    /**
     * Токена нет в whitelist: 401.
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

        verify(securityExceptionHandler).sendUnauthorized(request, response, SecurityErrorMessages.TOKEN_INVALID);
        assertThat(chain.getRequest()).isNull();
    }

    /**
     * Истёкший токен: 401 с TOKEN_EXPIRED.
     */
    @Test
    void doFilterInternal_returns401_whenTokenExpired() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtCookieService.extractToken(request)).thenReturn(TOKEN);
        when(jwtTokenService.extractSubject(TOKEN)).thenThrow(mock(ExpiredJwtException.class));

        jwtTokenFilter.doFilterInternal(request, response, chain);

        verify(securityExceptionHandler).sendUnauthorized(request, response, SecurityErrorMessages.TOKEN_EXPIRED);
        assertThat(chain.getRequest()).isNull();
    }

    /**
     * Невалидный токен: 401 с TOKEN_INVALID.
     */
    @Test
    void doFilterInternal_returns401_whenTokenInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtCookieService.extractToken(request)).thenReturn(TOKEN);
        when(jwtTokenService.extractSubject(TOKEN)).thenThrow(new RuntimeException());

        jwtTokenFilter.doFilterInternal(request, response, chain);

        verify(securityExceptionHandler).sendUnauthorized(request, response, SecurityErrorMessages.TOKEN_INVALID);
        assertThat(chain.getRequest()).isNull();
    }

    /**
     * Валидный токен: аутентификация в SecurityContext, запрос идёт дальше.
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
     * Публичный путь фильтр не проверяет.
     */
    @Test
    void shouldNotFilter_returnsTrue_forPublicPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PUBLIC_PATH);

        assertThat(jwtTokenFilter.shouldNotFilter(request)).isTrue();
    }

    /**
     * Защищённый путь фильтр проверяет.
     */
    @Test
    void shouldNotFilter_returnsFalse_forProtectedPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PROTECTED_PATH);

        assertThat(jwtTokenFilter.shouldNotFilter(request)).isFalse();
    }
}