package com.example.stark_vms_server.controller;

import com.example.stark_vms_server.dto.HealthResponse;
import com.example.stark_vms_server.dto.SendResponse;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private static final int DATABASE_PROBE_TIMEOUT_SECONDS = 2;

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/health")
    public ResponseEntity<SendResponse<HealthResponse>> health() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(SendResponse.of(HealthResponse.up(databaseConnected())));
    }

    private boolean databaseConnected() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DATABASE_PROBE_TIMEOUT_SECONDS);
        } catch (SQLException unreachable) {
            log.warn("Database health probe failed: {}", unreachable.getMessage());
            return false;
        }
    }
}
