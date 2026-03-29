package com.fimory.api.config;

import com.fimory.api.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "ok", "service", "fimory-java-api")));
    }

    @GetMapping("/health/db")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthDb() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "ok", "db", "up", "result", result)));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse<>(false, Map.of("status", "error", "db", "down", "message", ex.getMessage()), Map.of()));
        }
    }
}
