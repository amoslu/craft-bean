package com.craftbean.auth;

public record LoginResponse(String token, CurrentUserResponse user) {
}
