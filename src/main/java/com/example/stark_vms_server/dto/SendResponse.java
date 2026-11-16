package com.example.stark_vms_server.dto;

public record SendResponse<T>(T data) {

    public static <T> SendResponse<T> of(T data) {
        return new SendResponse<>(data);
    }
}
