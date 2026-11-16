package com.example.stark_vms_server.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(

        @NotBlank
        String jwtSecret,

        @Positive
        long tokenLifetimeSeconds,

        @Min(1)
        @Max(value = AuthConfig.BCRYPT_MAX_PASSWORD_BYTES,
                message = "must not exceed 72, because bcrypt ignores any input beyond 72 UTF-8 bytes")
        int maxPasswordBytes,

        String adminEmail,

        String adminPassword
) {
    public AuthProperties {
        adminEmail = adminEmail == null ? "" : adminEmail.strip();
        adminPassword = adminPassword == null ? "" : adminPassword;
    }
}
