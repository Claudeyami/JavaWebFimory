package com.fimory.api.auth;

public record AuthUserDto(Long id, String email, String displayName, String role) {
}
