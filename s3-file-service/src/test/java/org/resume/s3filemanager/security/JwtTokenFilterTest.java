package org.resume.s3filemanager.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.s3filemanager.constant.ApiPaths;
import org.resume.s3filemanager.enums.UserRole;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtTokenFilter - аутентификация по JWT, отказ решает слой авторизации")
class JwtTokenFilterTest {

    private static final String TOKEN = "test.jwt.token";
    private static final String USERNAME = "john";
    private static final String ROLE = UserRole.USER.getAuthority();
    private static final String PATH = ApiPaths.FILES;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private JwtWhitelistService jwtWhitelistService;

    @Mock
    private JwtCookieService jwtCookieService;

    @InjectMocks
    private JwtTokenFilter jwtTokenFilter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("doFilterInternal - без токена запрос идёт дальше по цепочке")
    void doFilterInternal_shouldContinueChain_whenTokenIsNull() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtCookieService.extractToken(request)).thenReturn(null);

        // when
        jwtTokenFilter.doFilterInternal(request, response, chain);

        // then
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(jwtTokenService, jwtWhitelistService);
    }

    @Test
    @DisplayName("doFilterInternal - токен не в whitelist: контекст очищен, запрос идёт дальше")
    void doFilterInternal_shouldContinueChain_whenTokenNotInWhitelist() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtCookieService.extractToken(request)).thenReturn(TOKEN);
        when(jwtTokenService.extractSubject(TOKEN)).thenReturn(USERNAME);
        when(jwtWhitelistService.isValid(USERNAME, TOKEN)).thenReturn(false);

        // when
        jwtTokenFilter.doFilterInternal(request, response, chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("doFilterInternal - истёкший токен: контекст очищен, запрос идёт дальше")
    void doFilterInternal_shouldContinueChain_whenTokenExpired() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtCookieService.extractToken(request)).thenReturn(TOKEN);
        when(jwtTokenService.extractSubject(TOKEN)).thenThrow(mock(ExpiredJwtException.class));

        // when
        jwtTokenFilter.doFilterInternal(request, response, chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("doFilterInternal - невалидный токен: контекст очищен, запрос идёт дальше")
    void doFilterInternal_shouldContinueChain_whenTokenInvalid() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtCookieService.extractToken(request)).thenReturn(TOKEN);
        when(jwtTokenService.extractSubject(TOKEN)).thenThrow(new RuntimeException());

        // when
        jwtTokenFilter.doFilterInternal(request, response, chain);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("doFilterInternal - валидный токен: аутентификация в контексте, запрос идёт дальше")
    void doFilterInternal_shouldSetAuthentication_whenTokenValid() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(jwtCookieService.extractToken(request)).thenReturn(TOKEN);
        when(jwtTokenService.extractSubject(TOKEN)).thenReturn(USERNAME);
        when(jwtWhitelistService.isValid(USERNAME, TOKEN)).thenReturn(true);
        when(jwtTokenService.extractRole(TOKEN)).thenReturn(ROLE);

        // when
        jwtTokenFilter.doFilterInternal(request, response, chain);

        // then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedUser(USERNAME, UserRole.USER));
        assertThat(chain.getRequest()).isNotNull();
    }
}
