package com.example.stark_vms_server.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_number", nullable = false, length = 64)
    private String vehicleNumber;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false, length = 100)
    private String company;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 32)
    private String status;

    protected Vehicle() {
    }

    public Vehicle(String vehicleNumber, String brand, String company,
                   String model, String type, String status) {
        this.vehicleNumber = vehicleNumber;
        this.brand = brand;
        this.company = company;
        this.model = model;
        this.type = type;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public String getBrand() {
        return brand;
    }

    public String getCompany() {
        return company;
    }

    public String getModel() {
        return model;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public void updateDetails(
            String vehicleNumber, String brand, String company,
            String model, String type, String status
    ) {
        this.vehicleNumber = vehicleNumber;
        this.brand = brand;
        this.company = company;
        this.model = model;
        this.type = type;
        this.status = status;
    }

    public enum Type {
        CAR, SEDAN, SUV, HATCHBACK, PICKUP, VAN, BUS, TRUCK, MOTORCYCLE, OTHER;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Type from(String value) {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        }
    }

    public enum Status {
        ACTIVE, INACTIVE, MAINTENANCE, RETIRED;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static Status from(String value) {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        }
    }
}
