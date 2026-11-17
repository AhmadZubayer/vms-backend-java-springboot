package com.example.stark_vms_server.config;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
