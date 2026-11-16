package com.example.stark_vms_server.dto;

public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) {
}
