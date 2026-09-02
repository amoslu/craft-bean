package com.craftbean.system.dto;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String name,
        String role,
        String status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt) {
}
