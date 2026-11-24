package org.resume.s3filemanager.security;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.resume.s3filemanager.constant.MdcConstants;
import org.resume.s3filemanager.enums.UserRole;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Аутентификация по JWT из cookie с проверкой по whitelist.
 * Отказ (401/403) решает слой авторизации, фильтр только наполняет SecurityContext.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final JwtWhitelistService jwtWhitelistService;
    private final JwtCookieService jwtCookieService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = jwtCookieService.extractToken(request);
        if (token != null) {
            authenticate(token, request);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Ставит аутентификацию при валидном токене, иначе очищает контекст.
     */
    private void authenticate(String token, HttpServletRequest request) {
        try {
            String username = jwtTokenService.extractSubject(token);

            if (!jwtWhitelistService.isValid(username, token)) {
                SecurityContextHolder.clearContext();
                log.warn("Token validation failed for user: {}", username);
                return;
            }

            UserRole role = UserRole.fromAuthority(jwtTokenService.extractRole(token));

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    new AuthenticatedUser(username, role),
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority(role.getAuthority()))
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            MDC.put(MdcConstants.USERNAME, username);

            log.debug("Authentication successful - User: {}, Role: {}", username, role);

        } catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();
            log.warn("Token expired for: {}", request.getRequestURI());
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            log.error("Invalid token for: {}. Error: {}", request.getRequestURI(), e.getMessage());
        }
    }
}
