package com.example.stark_vms_server.dto;

import com.example.stark_vms_server.entity.User;

public record UserResponse(String userEmail) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getUserEmail());
    }
}
