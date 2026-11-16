package com.example.stark_vms_server.dto;

import com.example.stark_vms_server.entity.Vehicle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VehicleWriteRequest(
        @NotBlank @Size(max = 64) String vehicleNumber,
        @NotBlank @Size(max = 100) String brand,
        @NotBlank @Size(max = 100) String company,
        @NotBlank @Size(max = 100) String model,
        @NotNull Vehicle.Type type,
        @NotNull Vehicle.Status status
) {
}
