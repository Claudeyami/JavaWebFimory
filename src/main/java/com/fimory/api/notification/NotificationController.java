package com.fimory.api.notification;

import com.fimory.api.common.ApiResponse;
import com.fimory.api.repository.UserRepository;
import com.fimory.api.security.AuthenticatedUser;
import com.fimory.api.security.CurrentUserProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public NotificationController(JdbcTemplate jdbcTemplate,
                                  UserRepository userRepository,
                                  CurrentUserProvider currentUserProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> notifications(@RequestParam(required = false) String email,
                                                                                 @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        Long userId = resolveUserId(email, emailHeader, null);
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        String sql = """
                SELECT NotificationID, UserID, Type, Title, Content, RelatedURL, IsRead, CreatedAt
                FROM Notifications
                WHERE UserID = ?
                ORDER BY CreatedAt DESC
                """;
        List<Map<String, Object>> items = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("NotificationID", rs.getLong("NotificationID"));
            item.put("UserID", rs.getLong("UserID"));
            item.put("Type", rs.getString("Type"));
            item.put("Title", rs.getString("Title"));
            item.put("Content", rs.getString("Content"));
            item.put("RelatedURL", rs.getString("RelatedURL"));
            item.put("IsRead", rs.getObject("IsRead") != null && rs.getBoolean("IsRead"));
            item.put("CreatedAt", rs.getObject("CreatedAt"));
            return item;
        }, userId);
        return ResponseEntity.ok(ApiResponse.ok(items));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> createNotification(@RequestBody(required = false) Map<String, Object> payload,
                                                                                @RequestParam(required = false) String email,
                                                                                @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        Long userId = resolveUserId(email, emailHeader, payload);
        if (userId == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, Map.of("message", "Missing user context"), Map.of()));
        }
        String type = asString(payload == null ? null : payload.get("type"), "System");
        String title = asString(payload == null ? null : payload.get("title"), "Thông báo");
        String content = asString(payload == null ? null : payload.get("content"), "");
        String relatedUrl = asString(payload == null ? null : payload.get("relatedUrl"), null);
        jdbcTemplate.update(
                """
                INSERT INTO Notifications (UserID, Type, Title, Content, RelatedURL, IsRead, CreatedAt)
                VALUES (?, ?, ?, ?, ?, 0, GETDATE())
                """,
                userId, type, title, content, relatedUrl
        );
        return ResponseEntity.ok(ApiResponse.ok(Map.of("created", true)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteNotification(@RequestParam(required = false) String email,
                                                                                @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        Long userId = resolveUserId(email, emailHeader, null);
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", false, "count", 0)));
        }
        int deleted = jdbcTemplate.update("DELETE FROM Notifications WHERE UserID = ?", userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true, "count", deleted)));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteSingleNotification(@PathVariable Long notificationId,
                                                                                      @RequestParam(required = false) String email,
                                                                                      @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        Long userId = resolveUserId(email, emailHeader, null);
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", false)));
        }
        int deleted = jdbcTemplate.update(
                "DELETE FROM Notifications WHERE NotificationID = ? AND UserID = ?",
                notificationId,
                userId
        );
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", deleted > 0, "count", deleted)));
    }

    @PostMapping("/read")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markRead(@RequestBody(required = false) Map<String, Object> payload) {
        String email = payload == null ? null : asString(payload.get("email"), null);
        Long userId = resolveUserId(email, null, payload);
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("updated", 0)));
        }
        Long notificationId = asLong(payload == null ? null : payload.get("notificationId"));
        int updated;
        if (notificationId != null) {
            updated = jdbcTemplate.update(
                    "UPDATE Notifications SET IsRead = 1 WHERE UserID = ? AND NotificationID = ?",
                    userId, notificationId
            );
        } else {
            updated = jdbcTemplate.update(
                    "UPDATE Notifications SET IsRead = 1 WHERE UserID = ? AND (IsRead = 0 OR IsRead IS NULL)",
                    userId
            );
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("updated", updated)));
    }

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> count(@RequestParam(required = false) String email,
                                                                  @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        Long userId = resolveUserId(email, emailHeader, null);
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("count", 0)));
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM Notifications WHERE UserID = ? AND (IsRead = 0 OR IsRead IS NULL)",
                Integer.class,
                userId
        );
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", count == null ? 0 : count)));
    }

    private Long resolveUserId(String emailQuery, String emailHeader, Map<String, Object> payload) {
        String email = normalize(emailHeader);
        if (email == null) {
            email = normalize(emailQuery);
        }
        if (email == null && payload != null && payload.get("email") != null) {
            email = normalize(String.valueOf(payload.get("email")));
        }
        if (email != null) {
            return userRepository.findByEmailIgnoreCase(email).map(user -> user.getId()).orElse(null);
        }
        try {
            AuthenticatedUser current = currentUserProvider.requireUser();
            return current.userId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
