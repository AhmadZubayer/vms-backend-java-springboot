package com.example.stark_vms_server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_users")
public class User {

    @Id
    @Column(name = "user_email", nullable = false, length = 80)
    private String userEmail;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    protected User() {
    }

    public User(String userEmail, String passwordHash) {
        this.userEmail = userEmail;
        this.passwordHash = passwordHash;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
