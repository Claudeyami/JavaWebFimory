package com.fimory.api.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateProfileRequest(
        String email,
        String displayName,
        String password,
        String username,
        String fullName,
        String avatar,
        String gender
) {
}
