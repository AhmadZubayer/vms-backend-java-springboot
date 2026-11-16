package com.example.stark_vms_server.config;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Browser origins allowed to call the API, read from
 * {@code APP_CORS_ALLOWED_ORIGIN_PATTERNS} so every environment sets its own.
 *
 * <p>Patterns accept the {@code *} wildcard, so {@code http://localhost:*}
 * covers the random port a Flutter web development server picks, and
 * {@code https://vms.example.com} names a deployed client exactly.
 */
@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(

        @NotEmpty(message = "Set APP_CORS_ALLOWED_ORIGIN_PATTERNS to the origins allowed to call this API.")
        List<String> allowedOriginPatterns
) {
    public CorsProperties {
        allowedOriginPatterns = allowedOriginPatterns == null
                ? List.of()
                : List.copyOf(allowedOriginPatterns);
    }
}
