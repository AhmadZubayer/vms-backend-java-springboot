package com.example.stark_vms_server.dto;

import com.example.stark_vms_server.entity.Vehicle;

public record VehicleResponse(
        Long id,
        String vehicleNumber,
        String brand,
        String company,
        String model,
        String type,
        String status
) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getVehicleNumber(),
                vehicle.getBrand(),
                vehicle.getCompany(),
                vehicle.getModel(),
                vehicle.getType(),
                vehicle.getStatus()
        );
    }
}
