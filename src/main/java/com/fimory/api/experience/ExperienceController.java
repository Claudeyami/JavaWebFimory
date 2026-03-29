package com.fimory.api.experience;

import com.fimory.api.domain.UserEntity;
import com.fimory.api.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/experience")
public class ExperienceController {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public ExperienceController(UserRepository userRepository, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/user")
    public Map<String, Object> getUserExperience(@RequestParam String email) {
        Long userId = findUserId(email);
        if (userId == null) {
            return buildExperience(0, 1);
        }
        int totalExp = resolveTotalExp(userId);
        int level = resolveLevel(userId, totalExp);
        return buildExperience(totalExp, level);
    }

    @PostMapping("/movie-watch")
    public Map<String, Object> awardMovieWatchExp(@RequestBody Map<String, Object> payload) {
        String email = payload == null ? null : asString(payload.get("email"));
        int watchedSeconds = payload == null ? 0 : asInt(payload.get("watchedSeconds"), 0);
        int gained = Math.max(1, Math.min(50, watchedSeconds / 60));
        return awardExp(email, gained, "Watch reward applied");
    }

    @PostMapping("/chapter-complete")
    public Map<String, Object> awardChapterCompleteExp(@RequestBody Map<String, Object> payload) {
        String email = payload == null ? null : asString(payload.get("email"));
        return awardExp(email, 25, "Chapter completion reward applied");
    }

    private Map<String, Object> awardExp(String email, int gained, String message) {
        Long userId = findUserId(email);
        if (userId == null) {
            return buildReward(0, 1, 0, message);
        }

        int currentExp = resolveTotalExp(userId);
        int newTotal = currentExp + gained;
        persistTotalExp(userId, newTotal);
        int level = resolveLevel(userId, newTotal);
        persistLevel(userId, level);
        return buildReward(newTotal, level, gained, message);
    }

    private Map<String, Object> buildReward(int totalExp, int level, int gained, String message) {
        int safeLevel = Math.max(level, 1);
        int currentLevelMin = (safeLevel - 1) * 100;
        int maxExp = safeLevel * 100;
        int currentLevelExp = Math.max(totalExp - currentLevelMin, 0);
        int expToNextLevel = Math.max(maxExp - totalExp, 0);
        return Map.of(
                "ok", true,
                "expGained", gained,
                "totalExp", totalExp,
                "level", safeLevel,
                "currentLevelExp", currentLevelExp,
                "maxExp", maxExp,
                "expToNextLevel", expToNextLevel,
                "message", message
        );
    }

    private Map<String, Object> buildExperience(int totalExp, int level) {
        int safeLevel = Math.max(level, 1);
        int currentLevelMin = (safeLevel - 1) * 100;
        int maxExp = safeLevel * 100;
        int currentLevelExp = Math.max(totalExp - currentLevelMin, 0);
        int expToNextLevel = Math.max(maxExp - totalExp, 0);
        return Map.of(
                "totalExp", totalExp,
                "level", safeLevel,
                "currentLevelExp", currentLevelExp,
                "maxExp", maxExp,
                "expToNextLevel", expToNextLevel
        );
    }

    private Long findUserId(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(email.trim())
                .map(UserEntity::getId)
                .orElse(null);
    }

    private int resolveTotalExp(Long userId) {
        try {
            if (tableExists("UserExperience")) {
                Integer value = jdbcTemplate.queryForObject(
                        "SELECT TOP 1 TotalExp FROM UserExperience WHERE UserID = ?",
                        Integer.class,
                        userId
                );
                return value == null ? 0 : value;
            }
            if (columnExists("Users", "TotalExp")) {
                Integer value = jdbcTemplate.queryForObject(
                        "SELECT TOP 1 TotalExp FROM Users WHERE UserID = ?",
                        Integer.class,
                        userId
                );
                return value == null ? 0 : value;
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private int resolveLevel(Long userId, int totalExp) {
        try {
            if (tableExists("UserExperience") && columnExists("UserExperience", "Level")) {
                Integer value = jdbcTemplate.queryForObject(
                        "SELECT TOP 1 Level FROM UserExperience WHERE UserID = ?",
                        Integer.class,
                        userId
                );
                if (value != null && value > 0) {
                    return value;
                }
            }
            if (columnExists("Users", "Level")) {
                Integer value = jdbcTemplate.queryForObject(
                        "SELECT TOP 1 Level FROM Users WHERE UserID = ?",
                        Integer.class,
                        userId
                );
                if (value != null && value > 0) {
                    return value;
                }
            }
        } catch (Exception ignored) {
        }
        return Math.max((totalExp / 100) + 1, 1);
    }

    private void persistTotalExp(Long userId, int totalExp) {
        try {
            if (tableExists("UserExperience")) {
                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM UserExperience WHERE UserID = ?",
                        Integer.class,
                        userId
                );
                if (exists != null && exists > 0) {
                    jdbcTemplate.update("UPDATE UserExperience SET TotalExp = ? WHERE UserID = ?", totalExp, userId);
                    return;
                }
                jdbcTemplate.update("INSERT INTO UserExperience (UserID, TotalExp, Level) VALUES (?, ?, ?)",
                        userId, totalExp, Math.max((totalExp / 100) + 1, 1));
                return;
            }
            if (columnExists("Users", "TotalExp")) {
                jdbcTemplate.update("UPDATE Users SET TotalExp = ? WHERE UserID = ?", totalExp, userId);
            }
        } catch (Exception ignored) {
        }
    }

    private void persistLevel(Long userId, int level) {
        try {
            if (tableExists("UserExperience") && columnExists("UserExperience", "Level")) {
                jdbcTemplate.update("UPDATE UserExperience SET Level = ? WHERE UserID = ?", level, userId);
                return;
            }
            if (columnExists("Users", "Level")) {
                jdbcTemplate.update("UPDATE Users SET Level = ? WHERE UserID = ?", level, userId);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
