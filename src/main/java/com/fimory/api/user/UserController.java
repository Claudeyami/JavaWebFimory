package com.fimory.api.user;

import com.fimory.api.auth.AuthUserDto;
import com.fimory.api.common.ApiResponse;
import com.fimory.api.favorite.FavoriteService;
import com.fimory.api.security.AuthenticatedUser;
import com.fimory.api.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestController
public class UserController {

    private final CurrentUserProvider currentUserProvider;
    private final UserService userService;
    private final FavoriteService favoriteService;
    private final JdbcTemplate jdbcTemplate;

    public UserController(CurrentUserProvider currentUserProvider,
                          UserService userService,
                          FavoriteService favoriteService,
                          JdbcTemplate jdbcTemplate) {
        this.currentUserProvider = currentUserProvider;
        this.userService = userService;
        this.favoriteService = favoriteService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Object>> me(@RequestParam(required = false) String email) {
        String targetEmail = email;
        if (targetEmail == null || targetEmail.isBlank()) {
            AuthenticatedUser current = currentUserProvider.requireUser();
            targetEmail = current.email();
        }
        AuthUserDto me = userService.getMe(targetEmail);
        String gender = userService.getGenderByEmail(targetEmail);
        Map<String, Object> legacyProfile = Map.of(
                "UserID", me.id(),
                "Email", me.email(),
                "Username", me.displayName() == null ? "" : me.displayName(),
                "FullName", me.displayName() == null ? "" : me.displayName(),
                "Role", me.role(),
                "Gender", gender,
                "gender", gender
        );
        return ResponseEntity.ok(ApiResponse.ok(legacyProfile));
    }

    @PostMapping("/me")
    public ResponseEntity<ApiResponse<AuthUserDto>> updateMe(@Valid @RequestBody UpdateProfileRequest request,
                                                             @RequestParam(required = false) String email) {
        String targetEmail = request.email();
        if (targetEmail == null || targetEmail.isBlank()) {
            targetEmail = email;
        }
        if (targetEmail == null || targetEmail.isBlank()) {
            AuthenticatedUser current = currentUserProvider.requireUser();
            targetEmail = current.email();
        }
        return ResponseEntity.ok(ApiResponse.ok(userService.updateMe(targetEmail, request)));
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<ApiResponse<Object>> updateAvatar() {
        return ResponseEntity.ok(ApiResponse.ok("TODO: implement avatar upload"));
    }

    @GetMapping({"/preferences", "/user/preferences"})
    public ResponseEntity<ApiResponse<Object>> preferences(@RequestParam(required = false) String email) {
        Long userId;
        if (email != null && !email.isBlank()) {
            userId = userService.getUserIdByEmail(email);
        } else {
            AuthenticatedUser current = currentUserProvider.requireUser();
            userId = current.userId();
        }
        UserPreferenceDto preference = userService.getPreferences(userId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.putAll(userService.getPreferenceMap(userId));
        payload.put("language", preference.language() == null ? "vi" : preference.language());
        payload.put("theme", preference.theme() == null ? "dark" : preference.theme());
        payload.put("auto_play", preference.autoPlay() != null && preference.autoPlay());
        payload.put("display_mode", "all");
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    @PostMapping({"/preferences", "/user/preferences"})
    public ResponseEntity<ApiResponse<Object>> updatePreferences(@RequestBody Map<String, Object> payload) {
        String email = payload.get("email") != null ? String.valueOf(payload.get("email")) : null;
        Long userId;
        if (email != null && !email.isBlank()) {
            userId = userService.getUserIdByEmail(email);
        } else {
            AuthenticatedUser current = currentUserProvider.requireUser();
            userId = current.userId();
        }

        String key = payload.get("key") != null ? String.valueOf(payload.get("key")) : null;
        String value = payload.get("value") != null ? String.valueOf(payload.get("value")) : null;

        if (key != null && !key.isBlank()) {
            String lowerKey = key.trim().toLowerCase();
            if ("language".equals(lowerKey) || "theme".equals(lowerKey) || "autoplay".equals(lowerKey) || "auto_play".equals(lowerKey)) {
                UserPreferenceDto current = userService.getPreferences(userId);
                String language = "language".equals(lowerKey) ? value : current.language();
                String theme = "theme".equals(lowerKey) ? value : current.theme();
                Boolean autoPlay = ("autoplay".equals(lowerKey) || "auto_play".equals(lowerKey))
                        ? Boolean.valueOf(value)
                        : current.autoPlay();
                userService.updatePreferences(userId, new UpdatePreferenceRequest(language, theme, autoPlay));
            } else {
                userService.savePreference(userId, key, value);
            }
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "success", true,
                    "key", key,
                    "value", value == null ? "" : value
            )));
        }

        String language = payload.get("language") != null ? String.valueOf(payload.get("language")) : null;
        String theme = payload.get("theme") != null ? String.valueOf(payload.get("theme")) : null;
        Boolean autoPlay = payload.get("autoPlay") != null ? Boolean.valueOf(String.valueOf(payload.get("autoPlay"))) : null;
        UserPreferenceDto updated = userService.updatePreferences(userId, new UpdatePreferenceRequest(language, theme, autoPlay));
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @GetMapping("/user/series-favorites")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> userSeriesFavorites(@RequestParam(required = false) String email) {
        String targetEmail = email;
        if (targetEmail == null || targetEmail.isBlank()) {
            try {
                targetEmail = currentUserProvider.requireUser().email();
            } catch (Exception ignored) {
                return ResponseEntity.ok(ApiResponse.ok(List.of()));
            }
        }
        List<Map<String, Object>> rows = favoriteService.getSeriesFavorites(targetEmail).stream()
                .map(seriesId -> Map.of("SeriesID", (Object) seriesId))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @GetMapping("/user/role-upgrade-requests")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> userRoleUpgradeRequests(@RequestParam(required = false) String email) {
        String targetEmail = email;
        if (targetEmail == null || targetEmail.isBlank()) {
            targetEmail = currentUserProvider.requireUser().email();
        }
        Long userId = userService.getUserIdByEmail(targetEmail);
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT r.RequestID,
                       r.RequestedRoleID,
                       rr.RoleName AS RequestedRoleName,
                       r.Reason,
                       r.Status,
                       r.ReviewNote,
                       r.RequestedAt,
                       r.ReviewedAt
                FROM RoleUpgradeRequests r
                LEFT JOIN Roles rr ON rr.RoleID = r.RequestedRoleID
                WHERE r.UserID = ?
                ORDER BY r.RequestedAt DESC, r.RequestID DESC
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("RequestID", rs.getLong("RequestID"));
                    row.put("RequestedRoleID", rs.getLong("RequestedRoleID"));
                    row.put("RequestedRoleName", normalizeRole(rs.getString("RequestedRoleName")));
                    row.put("Reason", rs.getString("Reason"));
                    row.put("Status", rs.getString("Status"));
                    row.put("ReviewNote", rs.getString("ReviewNote"));
                    row.put("RequestedAt", rs.getObject("RequestedAt"));
                    row.put("ReviewedAt", rs.getObject("ReviewedAt"));
                    return row;
                },
                userId
        );
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @PostMapping("/user/role-upgrade-request")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createRoleUpgradeRequest(@RequestBody(required = false) Map<String, Object> payload) {
        String requestedRole = payload != null && payload.get("requestedRole") != null
                ? String.valueOf(payload.get("requestedRole")).trim()
                : "";
        String reason = payload != null && payload.get("reason") != null
                ? String.valueOf(payload.get("reason")).trim()
                : "";
        if (requestedRole.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, Map.of("message", "requestedRole is required"), Map.of()));
        }
        if (reason.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, Map.of("message", "reason is required"), Map.of()));
        }

        AuthenticatedUser user = currentUserProvider.requireUser();
        Long userId = user.userId();
        String currentRole = normalizeRole(user.role());
        String normalizedRequestedRole = normalizeRole(requestedRole);
        if (Objects.equals(currentRole, normalizedRequestedRole)) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, Map.of("message", "Requested role is same as current role"), Map.of()));
        }
        if (roleRank(normalizedRequestedRole) <= roleRank(currentRole)) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, Map.of("message", "Requested role must be higher than current role"), Map.of()));
        }

        Long roleId = jdbcTemplate.query(
                "SELECT TOP 1 RoleID FROM Roles WHERE LOWER(RoleName) = LOWER(?)",
                rs -> rs.next() ? rs.getLong(1) : null,
                normalizedRequestedRole
        );
        if (roleId == null) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, Map.of("message", "Invalid requested role"), Map.of()));
        }

        Integer pending = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM RoleUpgradeRequests WHERE UserID = ? AND Status = 'Pending'",
                Integer.class,
                userId
        );
        if (pending != null && pending > 0) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(false, Map.of("message", "You already have a pending request"), Map.of()));
        }

        Long requestId = jdbcTemplate.query(
                """
                INSERT INTO RoleUpgradeRequests (UserID, RequestedRoleID, Reason, Status, RequestedAt)
                OUTPUT INSERTED.RequestID
                VALUES (?, ?, ?, 'Pending', GETDATE())
                """,
                rs -> rs.next() ? rs.getLong(1) : null,
                userId,
                roleId,
                reason
        );

        if (requestId == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, Map.of("message", "Failed to create role-upgrade request"), Map.of()));
        }

        jdbcTemplate.update(
                """
                INSERT INTO Notifications (UserID, Type, Title, Content, RelatedURL, IsRead, CreatedAt)
                SELECT u.UserID,
                       'RoleUpgradeRequest',
                       'Role upgrade request',
                       ?,
                       '/admin/users',
                       0,
                       GETDATE()
                FROM Users u
                LEFT JOIN Roles r ON r.RoleID = u.RoleID
                WHERE UPPER(COALESCE(r.RoleName, '')) = 'ADMIN'
                """,
                user.email() + " requested role upgrade to " + normalizedRequestedRole
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(Map.of(
                "requestId", requestId,
                "status", "Pending",
                "requestedRole", normalizedRequestedRole
        )));
    }

    private String normalizeRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Viewer";
        }
        String r = raw.trim().toUpperCase();
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

    private int roleRank(String role) {
        return switch (normalizeRole(role)) {
            case "Viewer" -> 0;
            case "Reup", "Translator", "Author", "Uploader" -> 1;
            case "Admin" -> 2;
            default -> 0;
        };
    }
}
