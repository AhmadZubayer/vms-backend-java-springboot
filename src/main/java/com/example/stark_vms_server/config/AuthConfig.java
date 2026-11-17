package com.example.stark_vms_server.config;

import com.example.stark_vms_server.dto.SendResponse;
import com.example.stark_vms_server.exception.FailedOperation;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfig {
    public static final String JWT_ISSUER = "stark-vms";
    public static final String JWT_AUDIENCE = "stark-vms-api";

    public static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    public static final int USER_EMAIL_MAX_LENGTH = 80;

    private static final int JWT_SECRET_MIN_BYTES = 32;

    private static final String[] DOCS_PATHS = {
            "/docs", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**"
    };

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, ObjectMapper json) throws Exception {
        BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
        AuthenticationEntryPoint unauthorized = (request, response, exception) -> {
            bearerEntryPoint.commence(request, response, exception);
            HttpStatus status = HttpStatus.valueOf(response.getStatus());
            writeProblem(json, request, response, status,
                    "Provide a valid bearer access token.");
        };

        BearerTokenAccessDeniedHandler bearerAccessDenied = new BearerTokenAccessDeniedHandler();
        AccessDeniedHandler forbidden = (request, response, exception) -> {
            bearerAccessDenied.handle(request, response, exception);
            writeProblem(json, request, response, HttpStatus.FORBIDDEN,
                    "Access to this resource is denied.");
        };

        return http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, DOCS_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                        .jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey jwtKey(AuthProperties auth) {
        byte[] key = Base64.getDecoder().decode(auth.jwtSecret());
        if (key.length < JWT_SECRET_MIN_BYTES) {
            throw new IllegalArgumentException("APP_JWT_SECRET must encode at least 32 random bytes.");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return NimbusJwtEncoder.withSecretKey(jwtKey).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(JWT_ISSUER),
                new JwtAudienceValidator(JWT_AUDIENCE)
        ));
        return decoder;
    }

    private static void writeProblem(
            ObjectMapper json,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String reason
    ) throws IOException {
        String detail = FailedOperation.message(
                request.getMethod(), request.getRequestURI(), reason
        );

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), SendResponse.of(status, detail, null));
    }
}
