package com.example.stark_vms_server.controller;

import com.example.stark_vms_server.dto.AccessTokenResponse;
import com.example.stark_vms_server.dto.LoginRequest;
import com.example.stark_vms_server.dto.SendResponse;
import com.example.stark_vms_server.dto.UserResponse;
import com.example.stark_vms_server.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<SendResponse<AccessTokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AccessTokenResponse token = authService.login(request.userEmail(), request.password())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid user email or password."
                ));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SendResponse.of(token));
    }

    @GetMapping("/me")
    public ResponseEntity<SendResponse<UserResponse>> currentUser(@AuthenticationPrincipal Jwt jwt) {
        UserResponse user = authService.findByUserEmail(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "This account no longer exists."
                ));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SendResponse.of(user));
    }
}
