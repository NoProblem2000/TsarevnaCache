package io.github.noproblem2000.tsarevnacache.security;

import io.github.noproblem2000.tsarevnacache.api.error.ApiErrorResponse;
import io.github.noproblem2000.tsarevnacache.api.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session based authentication endpoints for the SPA.
 *
 * <p>Typical flow:
 * <ol>
 *   <li>{@code GET /api/auth/csrf} – obtains the {@code XSRF-TOKEN} cookie.</li>
 *   <li>{@code POST /api/auth/login} with the {@code X-XSRF-TOKEN} header – opens a session.</li>
 *   <li>{@code GET /api/auth/me} – restores the logged-in user after a page reload.</li>
 *   <li>{@code POST /api/auth/logout} – handled by Spring Security's logout filter.</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    /**
     * Issues the CSRF cookie (written by {@link CsrfCookieFilter}); the body is empty.
     */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest login,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
        } catch (AuthenticationException e) {
            log.warn("Failed login attempt for user '{}' from {}", login.username(), request.getRemoteAddr());
            ErrorCode code = ErrorCode.AUTH_INVALID_CREDENTIALS;
            return ResponseEntity.status(code.status())
                    .body(ApiErrorResponse.of(code, "Invalid username or password", request.getRequestURI()));
        }

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        // The CSRF strategy replaced the token; load it now so the new cookie is sent with this response.
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }

        log.info("User '{}' logged in", authentication.getName());
        return ResponseEntity.ok(AuthUserResponse.from(authentication));
    }

    @GetMapping("/me")
    public AuthUserResponse me(Authentication authentication) {
        return AuthUserResponse.from(authentication);
    }

    /**
     * Login form submitted by the SPA.
     */
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {

        @Override
        public String toString() {
            return "LoginRequest[username=" + username + ", password=***]";
        }
    }

    /**
     * Currently authenticated user.
     *
     * @param username login name
     * @param roles    granted authorities without the {@code ROLE_} prefix
     */
    public record AuthUserResponse(String username, List<String> roles) {

        static AuthUserResponse from(Authentication authentication) {
            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                    .toList();
            return new AuthUserResponse(authentication.getName(), roles);
        }
    }
}
