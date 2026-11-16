package com.example.stark_vms_server.repository;

import com.example.stark_vms_server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
}
