package org.resume.s3filemanager.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.s3filemanager.constant.ApiPaths;
import org.resume.s3filemanager.constant.MdcConstants;
import org.resume.s3filemanager.constant.SecurityErrorMessages;
import org.resume.s3filemanager.dto.CommonResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

/**
 * Фильтр для аутентификации запросов на основе JWT токенов.
 * <p>
 * Извлекает JWT из cookie, проверяет его валидность через whitelist,
 * и устанавливает аутентификацию в SecurityContext для авторизованных запросов.
 * <p>
 * Публичные пути (login, register, home) пропускаются без проверки токена.
 *
 * @see JwtTokenService
 * @see JwtWhitelistService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final JwtWhitelistService jwtWhitelistService;
    private final JwtCookieService jwtCookieService;
    private final ObjectMapper objectMapper;

    private static final String[] PUBLIC_PATHS = {
            ApiPaths.AUTH + "/login",
            ApiPaths.AUTH + "/register",
            ApiPaths.HOME,
            "/swagger-ui",
            "/v3/api-docs"
    };

    /**
     * Определяет, должен ли фильтр пропустить данный запрос.
     * <p>
     * Публичные пути не требуют аутентификации:
     * <ul>
     *   <li>/api/v1/auth/login</li>
     *   <li>/api/v1/auth/register</li>
     *   <li>/api/v1/home</li>
     * </ul>
     *
     * @param request HTTP запрос
     * @return true если запрос к публичному пути, false в противном случае
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return Arrays.stream(PUBLIC_PATHS)
                .anyMatch(uri::startsWith);
    }

    /**
     * Выполняет фильтрацию запроса с проверкой JWT токена.
     * <p>
     * Извлекает токен из cookie, проверяет его в whitelist'е,
     * извлекает роль пользователя и устанавливает аутентификацию.
     * При ошибках валидации или истечении токена возвращает 401.
     *
     * @param request HTTP запрос
     * @param response HTTP ответ
     * @param filterChain цепочка фильтров
     * @throws ServletException при ошибке обработки запроса
     * @throws IOException при ошибке ввода-вывода
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = jwtCookieService.extractToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String username = jwtTokenService.extractSubject(token);

            if (!jwtWhitelistService.isValid(username, token)) {
                sendError(response, request.getRequestURI(), SecurityErrorMessages.TOKEN_INVALID);
                log.warn("Token validation failed for user: {}", username);
                return;
            }

            String role = jwtTokenService.extractRole(token);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    username,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority(role))
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            MDC.put(MdcConstants.USERNAME, username);

            log.debug("Authentication successful - User: {}, Role: {}", username, role);

        } catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();
            sendError(response, request.getRequestURI(), SecurityErrorMessages.TOKEN_EXPIRED);
            log.warn("Token expired for: {}", request.getRequestURI());
            return;
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            sendError(response, request.getRequestURI(), SecurityErrorMessages.TOKEN_INVALID);
            log.error("Invalid token for: {}. Error: {}", request.getRequestURI(), e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, String path, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/problem+json");

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, message);
        problemDetail.setProperty("timestamp", LocalDateTime.now());
        problemDetail.setProperty("path", path);

        CommonResponse<?> errorResponse = CommonResponse.error(problemDetail);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}
