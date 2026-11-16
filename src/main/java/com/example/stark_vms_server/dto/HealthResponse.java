package com.example.stark_vms_server.dto;

public record HealthResponse(String status, boolean databaseConnected, String database) {

    public static HealthResponse up(boolean databaseConnected) {
        return new HealthResponse(
                "UP", databaseConnected, databaseConnected ? "CONNECTED" : "DISCONNECTED"
        );
    }
}
