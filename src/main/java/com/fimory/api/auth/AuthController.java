package com.fimory.api.auth;

import com.fimory.api.common.ApiResponse;
import com.fimory.api.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class AuthController {

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    public AuthController(AuthService authService, CurrentUserProvider currentUserProvider) {
        this.authService = authService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<ApiResponse<AuthUserDto>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(authService.register(request)));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<AuthUserDto>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @GetMapping("/auth/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> loginInfo() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "message", "Use POST /api/auth/login with email and password",
                "method", "POST"
        )));
    }

    @GetMapping("/auth/role")
    public ResponseEntity<ApiResponse<Map<String, String>>> role() {
        String email = currentUserProvider.requireUser().email();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("role", authService.getRoleByEmail(email))));
    }

    @PostMapping("/auth/forgot-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "TODO: implement forgot password")));
    }

    @PostMapping("/auth/reset-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPassword() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "TODO: implement reset password")));
    }

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Map<String, String>>> sendOtp() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "TODO: implement send otp")));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyOtp() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "TODO: implement verify otp")));
    }
}
