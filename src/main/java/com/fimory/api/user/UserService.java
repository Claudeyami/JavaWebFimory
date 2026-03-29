package com.fimory.api.user;

import com.fimory.api.auth.AuthUserDto;
import com.fimory.api.common.NotFoundException;
import com.fimory.api.domain.UserEntity;
import com.fimory.api.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       JdbcTemplate jdbcTemplate,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthUserDto getMe(String email) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
        String profileName = user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName()
                : user.getDisplayName();
        return new AuthUserDto(
                user.getId(),
                user.getEmail(),
                profileName,
                normalizeRole(user.getRole() != null ? user.getRole().getName() : "USER")
        );
    }

    @Transactional
    public AuthUserDto updateMe(String email, UpdateProfileRequest request) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("User not found"));

        String nextDisplayName = request.displayName();
        if (nextDisplayName == null || nextDisplayName.isBlank()) {
            nextDisplayName = request.username();
        }
        if (nextDisplayName == null || nextDisplayName.isBlank()) {
            nextDisplayName = request.fullName();
        }
        if (nextDisplayName != null && !nextDisplayName.isBlank()) {
            user.setDisplayName(nextDisplayName);
        }
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.avatar() != null && !request.avatar().isBlank()) {
            user.setAvatar(request.avatar().trim());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        UserEntity saved = userRepository.save(user);
        if (request.gender() != null && !request.gender().isBlank()) {
            upsertSimplePreference(saved.getId(), "gender", normalizeGender(request.gender()));
        }
        String profileName = saved.getFullName() != null && !saved.getFullName().isBlank()
                ? saved.getFullName()
                : saved.getDisplayName();
        return new AuthUserDto(
                saved.getId(),
                saved.getEmail(),
                profileName,
                normalizeRole(saved.getRole() != null ? saved.getRole().getName() : "USER")
        );
    }

    public String getGenderByEmail(String email) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return getSimplePreference(user.getId(), "gender", "unknown");
    }

    public UserPreferenceDto getPreferences(Long userId) {
        String tableName = resolvePreferenceTable();
        if (tableName == null) {
            return new UserPreferenceDto("vi", "light", true);
        }

        List<String> columns = getPreferenceColumns(tableName);
        if (isKeyValuePreferenceTable(columns)) {
            Map<String, String> prefs = loadKeyValuePreferences(tableName, userId);
            String language = firstNonBlank(prefs.get("language"), prefs.get("lang"), "vi");
            String theme = firstNonBlank(prefs.get("theme"), prefs.get("displaytheme"), "light");
            String autoPlayRaw = firstNonBlank(prefs.get("auto_play"), prefs.get("autoplay"), prefs.get("isautoplay"), "true");
            return new UserPreferenceDto(language, theme, parseBoolean(autoPlayRaw));
        }

        String languageCol = resolveColumn(columns, List.of("Language", "Lang"));
        String themeCol = resolveColumn(columns, List.of("Theme", "DisplayTheme"));
        String autoPlayCol = resolveColumn(columns, List.of("AutoPlay", "Autoplay", "Auto_Play", "IsAutoPlay", "AutoPlayEnabled"));
        String userIdCol = resolveColumn(columns, List.of("UserID", "Userid", "UserId"));

        if (userIdCol == null) {
            return new UserPreferenceDto("vi", "light", true);
        }

        List<String> selected = new ArrayList<>();
        if (languageCol != null) selected.add(languageCol);
        if (themeCol != null) selected.add(themeCol);
        if (autoPlayCol != null) selected.add(autoPlayCol);

        if (selected.isEmpty()) {
            return new UserPreferenceDto("vi", "light", true);
        }

        String sql = "SELECT " + String.join(", ", selected) + " FROM " + tableName + " WHERE " + userIdCol + " = ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, userId);
        if (rows.isEmpty()) {
            return new UserPreferenceDto("vi", "light", true);
        }

        Map<String, Object> row = rows.getFirst();
        String language = languageCol != null && row.get(languageCol) != null ? String.valueOf(row.get(languageCol)) : "vi";
        String theme = themeCol != null && row.get(themeCol) != null ? String.valueOf(row.get(themeCol)) : "light";
        Boolean autoPlay = true;
        if (autoPlayCol != null && row.get(autoPlayCol) != null) {
            autoPlay = parseBoolean(row.get(autoPlayCol));
        }
        return new UserPreferenceDto(language, theme, autoPlay);
    }

    @Transactional
    public UserPreferenceDto updatePreferences(Long userId, UpdatePreferenceRequest request) {
        String tableName = resolvePreferenceTable();
        if (tableName == null) {
            return new UserPreferenceDto(
                    request.language() == null ? "vi" : request.language(),
                    request.theme() == null ? "light" : request.theme(),
                    request.autoPlay() == null || request.autoPlay()
            );
        }

        List<String> columns = getPreferenceColumns(tableName);
        if (isKeyValuePreferenceTable(columns)) {
            if (request.language() != null) {
                upsertSimplePreference(userId, "language", request.language());
            }
            if (request.theme() != null) {
                upsertSimplePreference(userId, "theme", request.theme());
            }
            if (request.autoPlay() != null) {
                upsertSimplePreference(userId, "auto_play", request.autoPlay().toString());
            }
            return getPreferences(userId);
        }

        String languageCol = resolveColumn(columns, List.of("Language", "Lang"));
        String themeCol = resolveColumn(columns, List.of("Theme", "DisplayTheme"));
        String autoPlayCol = resolveColumn(columns, List.of("AutoPlay", "Autoplay", "Auto_Play", "IsAutoPlay", "AutoPlayEnabled"));
        String userIdCol = resolveColumn(columns, List.of("UserID", "Userid", "UserId"));

        if (userIdCol == null) {
            return getPreferences(userId);
        }

        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (languageCol != null && request.language() != null) {
            setClauses.add(languageCol + " = ?");
            params.add(request.language());
        }
        if (themeCol != null && request.theme() != null) {
            setClauses.add(themeCol + " = ?");
            params.add(request.theme());
        }
        if (autoPlayCol != null && request.autoPlay() != null) {
            setClauses.add(autoPlayCol + " = ?");
            params.add(request.autoPlay());
        }

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM " + tableName + " WHERE " + userIdCol + " = ?",
                Integer.class,
                userId
        );
        boolean exists = count != null && count > 0;

        if (exists && !setClauses.isEmpty()) {
            String sql = "UPDATE " + tableName + " SET " + String.join(", ", setClauses) + " WHERE " + userIdCol + " = ?";
            params.add(userId);
            jdbcTemplate.update(sql, params.toArray());
        } else if (!exists) {
            List<String> insertCols = new ArrayList<>();
            List<String> insertMarks = new ArrayList<>();
            List<Object> insertParams = new ArrayList<>();

            insertCols.add(userIdCol);
            insertMarks.add("?");
            insertParams.add(userId);

            if (languageCol != null) {
                insertCols.add(languageCol);
                insertMarks.add("?");
                insertParams.add(request.language() == null ? "vi" : request.language());
            }
            if (themeCol != null) {
                insertCols.add(themeCol);
                insertMarks.add("?");
                insertParams.add(request.theme() == null ? "light" : request.theme());
            }
            if (autoPlayCol != null) {
                insertCols.add(autoPlayCol);
                insertMarks.add("?");
                insertParams.add(request.autoPlay() == null || request.autoPlay());
            }

            String sql = "INSERT INTO " + tableName + " (" + String.join(", ", insertCols) + ") VALUES (" + String.join(", ", insertMarks) + ")";
            jdbcTemplate.update(sql, insertParams.toArray());
        }

        return getPreferences(userId);
    }

    public Long getUserIdByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(UserEntity::getId)
                .orElseThrow(() -> new NotFoundException("User not found"));
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

    private String resolvePreferenceTable() {
        List<String> candidates = jdbcTemplate.queryForList(
                """
                        SELECT TABLE_NAME
                        FROM INFORMATION_SCHEMA.TABLES
                        WHERE TABLE_NAME IN ('UserPreferences', 'UserPreference')
                        """,
                String.class
        );
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.contains("UserPreferences")) {
            return "UserPreferences";
        }
        return candidates.getFirst();
    }

    private List<String> getPreferenceColumns(String tableName) {
        return jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?",
                String.class,
                tableName
        );
    }

    private String resolveColumn(List<String> existingColumns, List<String> candidates) {
        for (String candidate : candidates) {
            for (String existing : existingColumns) {
                if (existing.equalsIgnoreCase(candidate)) {
                    return existing;
                }
            }
        }
        return null;
    }

    private Boolean parseBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        String s = String.valueOf(value).trim().toLowerCase();
        return "1".equals(s) || "true".equals(s) || "yes".equals(s) || "on".equals(s);
    }

    private boolean isKeyValuePreferenceTable(List<String> existingColumns) {
        String keyCol = resolveColumn(existingColumns, List.of("PreferenceKey"));
        String valueCol = resolveColumn(existingColumns, List.of("PreferenceValue"));
        String userIdCol = resolveColumn(existingColumns, List.of("UserID", "UserId", "Userid"));
        return keyCol != null && valueCol != null && userIdCol != null;
    }

    private Map<String, String> loadKeyValuePreferences(String tableName, Long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT PreferenceKey, PreferenceValue FROM " + tableName + " WHERE UserID = ?",
                userId
        );
        Map<String, String> prefs = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get("PreferenceKey");
            Object value = row.get("PreferenceValue");
            if (key != null) {
                prefs.put(String.valueOf(key).trim().toLowerCase(), value == null ? "" : String.valueOf(value));
            }
        }
        return prefs;
    }

    private String getSimplePreference(Long userId, String key, String defaultValue) {
        String tableName = resolvePreferenceTable();
        if (tableName == null) {
            return defaultValue;
        }
        List<String> cols = getPreferenceColumns(tableName);
        if (!isKeyValuePreferenceTable(cols)) {
            return defaultValue;
        }
        List<String> values = jdbcTemplate.queryForList(
                "SELECT PreferenceValue FROM " + tableName + " WHERE UserID = ? AND LOWER(PreferenceKey) = LOWER(?)",
                String.class,
                userId,
                key
        );
        if (values.isEmpty()) {
            return defaultValue;
        }
        String value = values.getFirst();
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void upsertSimplePreference(Long userId, String key, String value) {
        String tableName = resolvePreferenceTable();
        if (tableName == null) {
            return;
        }
        List<String> cols = getPreferenceColumns(tableName);
        if (!isKeyValuePreferenceTable(cols)) {
            return;
        }

        int updated = jdbcTemplate.update(
                "UPDATE " + tableName + " SET PreferenceValue = ?, UpdatedAt = GETDATE() WHERE UserID = ? AND LOWER(PreferenceKey) = LOWER(?)",
                value,
                userId,
                key
        );

        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO " + tableName + " (UserID, PreferenceKey, PreferenceValue, UpdatedAt) VALUES (?, ?, ?, GETDATE())",
                    userId,
                    key,
                    value
            );
        }
    }

    private String normalizeGender(String raw) {
        if (raw == null) {
            return "unknown";
        }
        String g = raw.trim().toLowerCase();
        if (g.equals("male") || g.equals("nam") || g.equals("m")) {
            return "male";
        }
        if (g.equals("female") || g.equals("nu") || g.equals("nữ") || g.equals("f")) {
            return "female";
        }
        return "unknown";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
