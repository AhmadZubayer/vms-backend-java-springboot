package com.example.stark_vms_server.dto;

import java.util.Map;

public record VehicleStatsResponse(long total, Map<String, Long> byStatus) {
}
