package com.fimory.api.auth;

import com.fimory.api.common.NotFoundException;
import com.fimory.api.domain.RoleEntity;
import com.fimory.api.domain.UserEntity;
import com.fimory.api.repository.RoleRepository;
import com.fimory.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean acceptAnyPassword;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.auth.accept-any-password:false}") boolean acceptAnyPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.acceptAnyPassword = acceptAnyPassword;
    }

    @Transactional
    public AuthUserDto register(RegisterRequest request) {
        userRepository.findByEmailIgnoreCase(request.email()).ifPresent(existing -> {
            throw new IllegalArgumentException("Email already exists");
        });

        RoleEntity role = roleRepository.findByNameIgnoreCase("USER")
                .orElseGet(() -> {
                    RoleEntity userRole = new RoleEntity();
                    userRole.setId(1L);
                    userRole.setName("USER");
                    return roleRepository.save(userRole);
                });

        UserEntity user = new UserEntity();
        user.setEmail(request.email());
        user.setDisplayName(request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);

        UserEntity saved = userRepository.save(user);
        return map(saved);
    }

    public AuthUserDto login(LoginRequest request) {
        UserEntity user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new NotFoundException("Invalid email or password"));

        if (!acceptAnyPassword && !isPasswordValid(request.password(), user.getPasswordHash())) {
            throw new NotFoundException("Invalid email or password");
        }

        return map(user);
    }

    public String getRoleByEmail(String email) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return normalizeRole(user.getRole() != null ? user.getRole().getName() : "USER");
    }

    public AuthUserDto findByEmail(String email) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return map(user);
    }

    private AuthUserDto map(UserEntity user) {
        return new AuthUserDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                normalizeRole(user.getRole() != null ? user.getRole().getName() : "USER")
        );
    }

    private String normalizeRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return "Viewer";
        }
        String normalized = rawRole.trim().toUpperCase();
        return switch (normalized) {
            case "ADMIN" -> "Admin";
            case "UPLOADER" -> "Uploader";
            case "AUTHOR" -> "Author";
            case "TRANSLATOR" -> "Translator";
            case "REUP" -> "Reup";
            case "USER", "VIEWER" -> "Viewer";
            default -> normalized.substring(0, 1) + normalized.substring(1).toLowerCase();
        };
    }

    private boolean isPasswordValid(String rawPassword, String storedPassword) {
        if (storedPassword == null || storedPassword.isBlank()) {
            return false;
        }
        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return rawPassword.equals(storedPassword);
    }
}
