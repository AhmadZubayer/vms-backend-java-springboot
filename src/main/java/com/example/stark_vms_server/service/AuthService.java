package com.example.stark_vms_server.service;

import static com.example.stark_vms_server.config.AuthConfig.JWT_AUDIENCE;
import static com.example.stark_vms_server.config.AuthConfig.JWT_ISSUER;
import static com.example.stark_vms_server.config.AuthConfig.USER_EMAIL_MAX_LENGTH;

import com.example.stark_vms_server.config.AuthProperties;
import com.example.stark_vms_server.dto.AccessTokenResponse;
import com.example.stark_vms_server.dto.UserResponse;
import com.example.stark_vms_server.entity.User;
import com.example.stark_vms_server.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder tokens;
    private final AuthProperties auth;
    private final String fallbackHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtEncoder tokens,
                       AuthProperties auth) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.auth = auth;
        this.fallbackHash = passwords.encode("placeholder-password-for-unknown-accounts");
    }

    @Override
    public void run(ApplicationArguments args) {
        String userEmail = auth.adminEmail();
        String password = auth.adminPassword();

        if (userEmail.isEmpty() && password.isEmpty()) {
            return;
        }
        if (userEmail.isEmpty() || userEmail.length() > USER_EMAIL_MAX_LENGTH || password.isBlank()
                || passwordBytes(password) > auth.maxPasswordBytes()) {
            throw new IllegalArgumentException(
                    "Set both admin credentials and use a password no longer than "
                            + auth.maxPasswordBytes() + " UTF-8 bytes."
            );
        }
        if (users.existsById(userEmail)) {
            return;
        }

        try {
            users.save(new User(userEmail, passwords.encode(password)));
        } catch (DataIntegrityViolationException alreadySeeded) {
            return;
        }
    }

    @Transactional(readOnly = true)
    public Optional<AccessTokenResponse> login(String userEmail, String password) {
        if (passwordBytes(password) > auth.maxPasswordBytes()) {
            return Optional.empty();
        }

        String account = userEmail.strip();
        Optional<String> hash = findHash(account);
        if (!passwords.matches(password, hash.orElse(fallbackHash)) || hash.isEmpty()) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JWT_ISSUER)
                .subject(account)
                .audience(List.of(JWT_AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(auth.tokenLifetimeSeconds()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = tokens.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return Optional.of(new AccessTokenResponse(
                token, OAuth2AccessToken.TokenType.BEARER.getValue(), auth.tokenLifetimeSeconds()
        ));
    }

    @Transactional(readOnly = true)
    public Optional<UserResponse> findByUserEmail(String userEmail) {
        return users.findById(userEmail.strip())
                .map(UserResponse::from);
    }

    private Optional<String> findHash(String userEmail) {
        return users.findById(userEmail)
                .map(User::getPasswordHash);
    }

    private static int passwordBytes(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length;
    }
}
