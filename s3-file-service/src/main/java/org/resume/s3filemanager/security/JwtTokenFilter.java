package org.resume.s3filemanager.security;

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
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

/**
 * Аутентификация по JWT из cookie с проверкой по whitelist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final JwtWhitelistService jwtWhitelistService;
    private final JwtCookieService jwtCookieService;
    private final SecurityExceptionHandler securityExceptionHandler;

    private static final String[] PUBLIC_PATHS = {
            ApiPaths.AUTH + "/login",
            ApiPaths.AUTH + "/register",
            ApiPaths.HOME,
            "/swagger-ui",
            "/v3/api-docs"
    };

    /** Публичные пути пропускаются без проверки токена. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return Arrays.stream(PUBLIC_PATHS)
                .anyMatch(uri::startsWith);
    }

    /** Без токена запрос идёт дальше, при невалидном или истёкшем токене - 401. */
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
                securityExceptionHandler.sendUnauthorized(request, response, SecurityErrorMessages.TOKEN_INVALID);
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
            securityExceptionHandler.sendUnauthorized(request, response, SecurityErrorMessages.TOKEN_EXPIRED);
            log.warn("Token expired for: {}", request.getRequestURI());
            return;
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            securityExceptionHandler.sendUnauthorized(request, response, SecurityErrorMessages.TOKEN_INVALID);
            log.error("Invalid token for: {}. Error: {}", request.getRequestURI(), e.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }
}
