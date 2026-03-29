package com.fimory.api.admin;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminUserDto(
        @JsonProperty("UserID") Long id,
        @JsonProperty("Username") String username,
        @JsonProperty("Email") String email,
        @JsonProperty("FullName") String fullName,
        @JsonProperty("RoleName") String roleName,
        @JsonProperty("Level") Integer level,
        @JsonProperty("IsActive") Boolean isActive,
        @JsonProperty("IsEmailVerified") Boolean isEmailVerified,
        @JsonProperty("CreatedAt") String createdAt,
        @JsonProperty("LastLoginAt") String lastLoginAt
) {
}
