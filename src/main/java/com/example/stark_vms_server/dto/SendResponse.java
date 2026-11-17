package com.example.stark_vms_server.dto;

import org.springframework.http.HttpStatus;

public record SendResponse<T>(int statusCode, String message, T data) {
    public static <T> SendResponse<T> of(HttpStatus status, String message, T data) {
        return new SendResponse<>(status.value(), message, data);
    }

    public static <T> SendResponse<T> of(int statusCode, String message, T data) {
        return new SendResponse<>(statusCode, message, data);
    }

    public static <T> SendResponse<T> ok(String message, T data) {
        return of(HttpStatus.OK, message, data);
    }

    public static <T> SendResponse<T> created(String message, T data) {
        return of(HttpStatus.CREATED, message, data);
    }

    public static SendResponse<Void> done(String message) {
        return of(HttpStatus.OK, message, null);
    }
}
