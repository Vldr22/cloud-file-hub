package org.resume.s3filemanager.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MySecurityUtilsTest {

    private static final String IPV6_FULL = "0:0:0:0:0:0:0:1";

    /**
     * Request равен null: возвращается null.
     */
    @Test
    void extractClientIp_returnsNull_whenRequestIsNull() {
        assertThat(MySecurityUtils.extractClientIp(null)).isNull();
    }

    /**
     * Заголовок X-Forwarded-For намеренно игнорируется, возвращается RemoteAddr.
     */
    @Test
    void extractClientIp_ignoresXForwardedFor_returnsRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        request.setRemoteAddr("192.168.1.100");

        assertThat(MySecurityUtils.extractClientIp(request)).isEqualTo("192.168.1.100");
    }

    /**
     * Есть заголовок X-Real-IP: возвращается он.
     */
    @Test
    void extractClientIp_returnsXRealIp_whenNoXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "203.0.113.10");

        assertThat(MySecurityUtils.extractClientIp(request)).isEqualTo("203.0.113.10");
    }

    /**
     * Полный IPv6 localhost нормализуется в 127.0.0.1.
     */
    @Test
    void extractClientIp_normalizesIPv6Localhost_toIPv4() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(IPV6_FULL);

        assertThat(MySecurityUtils.extractClientIp(request)).isEqualTo("127.0.0.1");
    }

    /**
     * Сокращённый IPv6 localhost нормализуется в 127.0.0.1.
     */
    @Test
    void extractClientIp_normalizesShortIPv6Localhost_toIPv4() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("::1");

        assertThat(MySecurityUtils.extractClientIp(request)).isEqualTo("127.0.0.1");
    }

    /**
     * Нет заголовков: возвращается RemoteAddr.
     */
    @Test
    void extractClientIp_returnsRemoteAddr_whenNoHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");

        assertThat(MySecurityUtils.extractClientIp(request)).isEqualTo("192.168.1.100");
    }
}
