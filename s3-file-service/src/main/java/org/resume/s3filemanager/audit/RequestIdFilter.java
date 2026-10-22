package org.resume.s3filemanager.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.resume.s3filemanager.constant.MdcConstants;
import org.resume.s3filemanager.constant.SecurityConstants;
import org.resume.s3filemanager.security.MySecurityUtils;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Фильтр для генерации уникального идентификатора запроса и настройки MDC контекста.
 * <p>
 * Добавляет в MDC:
 * <ul>
 *   <li>requestId - уникальный идентификатор запроса для трейсинга</li>
 *   <li>Username - "anonymous", для аутентифицированного запроса имя ставит JwtTokenFilter</li>
 *   <li>Ip - IP адрес клиента</li>
 * </ul>
 * Эти значения автоматически включаются во все логи в рамках запроса.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String requestId = UUID.randomUUID().toString();
            String ip = MySecurityUtils.extractClientIp(request);

            MDC.put(MdcConstants.REQUEST_ID, requestId);
            MDC.put(MdcConstants.USERNAME, SecurityConstants.ANONYMOUS);
            MDC.put(MdcConstants.IP_ADDRESS, ip);

            log.debug("Request started: {} {}", request.getMethod(), request.getRequestURI());

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}