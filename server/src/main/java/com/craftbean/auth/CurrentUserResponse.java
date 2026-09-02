package com.craftbean.auth;

public record CurrentUserResponse(Long id, String username, String name, String role) {
}
