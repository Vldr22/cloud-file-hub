package org.resume.s3filemanager.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.resume.s3filemanager.constant.ErrorMessages;
import org.resume.s3filemanager.dto.CommonResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Ответы об отказе на уровне фильтр-цепочки: 401 (не аутентифицирован) и 403 (нет прав).
 */
@Component
@RequiredArgsConstructor
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        sendUnauthorized(request, response, ErrorMessages.AUTHENTICATION_REQUIRED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, ErrorMessages.INSUFFICIENT_PERMISSIONS);
    }

    public void sendUnauthorized(HttpServletRequest request, HttpServletResponse response,
                                 String message) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, message);
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, message);
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        objectMapper.writeValue(response.getWriter(), CommonResponse.error(problemDetail));
    }
}
