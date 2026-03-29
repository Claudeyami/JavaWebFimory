package com.fimory.api.admin;

import com.fimory.api.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<AdminUserDto> getUsers() {
        return userRepository.findAll().stream()
                .map(user -> new AdminUserDto(
                        user.getId(),
                        user.getDisplayName(),
                        user.getEmail(),
                        user.getDisplayName(),
                        normalizeRole(user.getRole() != null ? user.getRole().getName() : "USER"),
                        1,
                        true,
                        true,
                        null,
                        null
                ))
                .toList();
    }

    private String normalizeRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) return "Viewer";
        String r = rawRole.trim().toUpperCase();
        return switch (r) {
            case "ADMIN" -> "Admin";
            case "UPLOADER" -> "Uploader";
            case "AUTHOR" -> "Author";
            case "TRANSLATOR" -> "Translator";
            case "REUP" -> "Reup";
            case "USER", "VIEWER" -> "Viewer";
            default -> r.substring(0, 1) + r.substring(1).toLowerCase();
        };
    }
}
