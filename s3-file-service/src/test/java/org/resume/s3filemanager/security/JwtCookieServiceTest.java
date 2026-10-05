package org.resume.s3filemanager.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.resume.s3filemanager.constant.SecurityConstants;
import org.resume.s3filemanager.properties.SecurityProperties;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtCookieServiceTest {

    private static final String TOKEN = "my.jwt.token";

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private SecurityProperties securityProperties;

    @InjectMocks
    private JwtCookieService jwtCookieService;


    // setAuthCookie
    /**
     * Cookie устанавливается с токеном и обязательными атрибутами безопасности.
     */
    @Test
    void setAuthCookie_addsSetCookieHeader_withToken() {
        when(securityProperties.isCookieSecure()).thenReturn(false);
        when(jwtTokenService.getExpirationSeconds()).thenReturn(3600L);

        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtCookieService.setAuthCookie(response, TOKEN);

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie)
                .contains(SecurityConstants.COOKIE_NAME + "=" + TOKEN)
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/")
                .contains("Max-Age=3600");
    }

    // clearAuthCookie

    /**
     * Cookie очищается установкой Max-Age=0.
     */
    @Test
    void clearAuthCookie_setsMaxAgeToZero() {
        when(securityProperties.isCookieSecure()).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtCookieService.clearAuthCookie(response);

        assertThat(response.getHeader("Set-Cookie"))
                .contains(SecurityConstants.COOKIE_NAME + "=")
                .contains("Max-Age=0");
    }

    // extractToken

    /**
     * Cookie отсутствуют — возвращается null.
     */
    @Test
    void extractToken_returnsNull_whenNoCookies() {
        assertThat(jwtCookieService.extractToken(new MockHttpServletRequest())).isNull();
    }

    /**
     * Нужный cookie присутствует — возвращается его значение.
     */
    @Test
    void extractToken_returnsToken_whenCookiePresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(SecurityConstants.COOKIE_NAME, TOKEN));

        assertThat(jwtCookieService.extractToken(request)).isEqualTo(TOKEN);
    }

    /**
     * Среди нескольких cookie — возвращается значение нужного.
     */
    @Test
    void extractToken_returnsCorrectToken_whenMultipleCookiesPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie("other_cookie", "other_value"),
                new Cookie(SecurityConstants.COOKIE_NAME, TOKEN)
        );

        assertThat(jwtCookieService.extractToken(request)).isEqualTo(TOKEN);
    }

    /**
     * Cookie с другим именем — возвращается null.
     */
    @Test
    void extractToken_returnsNull_whenCookieNameDoesNotMatch() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("wrong_name", TOKEN));

        assertThat(jwtCookieService.extractToken(request)).isNull();
    }
}