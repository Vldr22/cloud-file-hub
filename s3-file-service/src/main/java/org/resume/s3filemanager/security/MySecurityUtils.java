package org.resume.s3filemanager.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Утилитарный класс для получения IP адреса клиента.
 */
@Slf4j
@UtilityClass
public class MySecurityUtils {

    /**
     * Извлекает IP адрес клиента из HTTP запроса.
     * <p>
     * Доверяет только заголовку X-Real-IP, проставляемому Nginx.
     * X-Forwarded-For намеренно игнорируется защищая от возможной подделки клиента IP.
     *
     * @param request HTTP запрос
     * @return IP адрес клиента или null
     */
    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }

        String remoteAddr = request.getRemoteAddr();

        // Localhost normalization для единообразия
        if ("0:0:0:0:0:0:0:1".equals(remoteAddr) || "::1".equals(remoteAddr)) {
            return "127.0.0.1";
        }

        return remoteAddr;
    }
}