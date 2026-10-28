package io.github.noproblem2000.tsarevnacache.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the single built-in administrator account ({@code admin.security.*}).
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "admin.security")
public class AdminSecurityProperties {

    /** Default credentials that trigger a startup warning when left unchanged. */
    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "admin";

    /** Administrator login. */
    @NotBlank
    private String username = DEFAULT_USERNAME;

    /**
     * Administrator password. Plain text is treated as {@code {noop}}; values with an encoder
     * prefix such as {@code {bcrypt}} are used as is.
     */
    @NotBlank
    private String password = DEFAULT_PASSWORD;

    @Valid
    private Cors cors = new Cors();

    @Valid
    private LoginThrottle loginThrottle = new LoginThrottle();

    /** Brute-force protection of {@code POST /api/auth/login}, per client address. */
    @Getter
    @Setter
    public static class LoginThrottle {

        /** Failed logins allowed within {@link #window} before further attempts are rejected. */
        @Min(1)
        private int maxFailures = 10;

        /** Sliding window for counting failures; also the lockout duration. */
        @NotNull
        private Duration window = Duration.ofMinutes(5);
    }

    /** Cross-origin settings, only needed when the UI and API are served from different origins. */
    @Getter
    @Setter
    public static class Cors {

        /** Exact origins allowed to call the API with credentials, e.g. {@code http://localhost:5173}. */
        private List<String> allowedOrigins = new ArrayList<>();
    }
}
