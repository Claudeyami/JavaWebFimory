package com.fimory.api.security;

import java.util.List;

public record AuthenticatedUser(Long userId, String email, String role, List<String> permissions) {
}
