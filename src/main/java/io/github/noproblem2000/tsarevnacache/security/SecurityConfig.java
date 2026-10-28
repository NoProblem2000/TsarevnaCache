package io.github.noproblem2000.tsarevnacache.security;

import io.github.noproblem2000.tsarevnacache.config.properties.AdminSecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security setup for the REST API and the bundled SPA.
 *
 * <ul>
 *   <li>The SPA authenticates via {@code POST /api/auth/login} and keeps a server side session
 *       (HTTP-only, SameSite=Strict cookie).</li>
 *   <li>State changing requests are protected by a cookie based CSRF token
 *       ({@code XSRF-TOKEN} cookie, {@code X-XSRF-TOKEN} header).</li>
 *   <li>HTTP Basic is accepted as well for scripts and tools such as {@code curl}; such requests
 *       carry explicit credentials and are therefore exempt from CSRF checks.</li>
 *   <li>Everything under {@code /api/**} requires the administrator account; static UI resources
 *       are public.</li>
 * </ul>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    public static final String ROLE_ADMIN = "ADMIN";

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                                                      AuthenticationManager authenticationManager,
                                                      CsrfTokenRepository csrfTokenRepository,
                                                      SecurityContextRepository securityContextRepository,
                                                      JsonSecurityErrorHandler errorHandler,
                                                      @Value("${server.servlet.session.cookie.name:JSESSIONID}")
                                                      String sessionCookieName) throws Exception {
        http
                .authenticationManager(authenticationManager)
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers(SecurityConfig::hasBasicCredentials))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers("/api/**").hasRole(ROLE_ADMIN)
                        .anyRequest().permitAll())
                .httpBasic(basic -> basic.authenticationEntryPoint(errorHandler))
                .formLogin(AbstractHttpConfigurer::disable)
                .rememberMe(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies(sessionCookieName))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * The single administrator account taken from {@code admin.security.*}.
     */
    @Bean
    public UserDetailsService userDetailsService(AdminSecurityProperties properties) {
        if (AdminSecurityProperties.DEFAULT_USERNAME.equals(properties.getUsername())
                && AdminSecurityProperties.DEFAULT_PASSWORD.equals(properties.getPassword())) {
            log.warn("The default administrator credentials are in use. "
                    + "Set ADMIN_USERNAME and ADMIN_PASSWORD before exposing this service.");
        }
        return new InMemoryUserDetailsManager(User.withUsername(properties.getUsername())
                .password(withEncoderPrefix(properties.getPassword()))
                .roles(ROLE_ADMIN)
                .build());
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * Cookie readable by JavaScript so the SPA can echo it in the {@code X-XSRF-TOKEN} header.
     */
    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));
        return repository;
    }

    /**
     * Same repository layout as Spring Security's default, exposed as a bean so the login endpoint
     * persists the context exactly where the filter chain reads it.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    /**
     * Applied by the login endpoint: rotates the session id (fixation protection) and issues a
     * fresh CSRF token.
     */
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        CsrfAuthenticationStrategy csrfStrategy = new CsrfAuthenticationStrategy(csrfTokenRepository);
        csrfStrategy.setRequestHandler(new SpaCsrfTokenRequestHandler());
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                csrfStrategy));
    }

    /**
     * CORS is disabled unless {@code admin.security.cors.allowed-origins} lists explicit origins.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AdminSecurityProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = properties.getCors().getAllowedOrigins().stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .toList();
        if (!origins.isEmpty()) {
            CorsConfiguration cors = new CorsConfiguration();
            cors.setAllowedOrigins(origins);
            cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            cors.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.AUTHORIZATION, "X-XSRF-TOKEN"));
            cors.setAllowCredentials(true);
            cors.setMaxAge(3600L);
            source.registerCorsConfiguration("/api/**", cors);
            log.info("CORS enabled for origins {}", origins);
        }
        return source;
    }

    private static boolean hasBasicCredentials(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.regionMatches(true, 0, "Basic ", 0, 6);
    }

    /**
     * Plain text passwords from the configuration are marked as {@code {noop}} so the delegating
     * encoder accepts them; values that already carry an encoder id are kept.
     */
    private static String withEncoderPrefix(String password) {
        return password.startsWith("{") && password.indexOf('}') > 1 ? password : "{noop}" + password;
    }
}
