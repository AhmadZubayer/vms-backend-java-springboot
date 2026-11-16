package com.example.stark_vms_server.dto;

import com.example.stark_vms_server.config.AuthConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = AuthConfig.USER_EMAIL_MAX_LENGTH) String userEmail,
        @NotBlank @Size(max = AuthConfig.BCRYPT_MAX_PASSWORD_BYTES) String password
) {
}
