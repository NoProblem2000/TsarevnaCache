package io.github.noproblem2000.tsarevnacache.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.noproblem2000.tsarevnacache.api.error.ApiErrorResponse;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * Writes authentication and authorization failures as {@link ApiErrorResponse} JSON.
 *
 * <p>No {@code WWW-Authenticate} header is sent, so browsers never show the native Basic-auth
 * dialog; the SPA handles 401 responses by redirecting to its own login screen.
 */
@Component
@RequiredArgsConstructor
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(request, response, ErrorCode.AUTH_REQUIRED, "Authentication is required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        if (accessDeniedException instanceof CsrfException) {
            write(request, response, ErrorCode.CSRF_TOKEN_INVALID, "Missing or invalid CSRF token");
        } else {
            write(request, response, ErrorCode.ACCESS_DENIED, "Access is denied");
        }
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       ErrorCode code, String message) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(code, message, request.getRequestURI()));
    }
}
