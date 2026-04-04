package com.fimory.api.experience;

import com.fimory.api.domain.UserEntity;
import com.fimory.api.domain.UserExpEntity;
import com.fimory.api.repository.UserActivityRepository;
import com.fimory.api.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ExperienceService {

    private final UserRepository userRepository;
    private final UserActivityRepository userActivityRepository;
    private final JdbcTemplate jdbcTemplate;

    public ExperienceService(UserRepository userRepository,
                             UserActivityRepository userActivityRepository,
                             JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.userActivityRepository = userActivityRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getUserExperience(String email) {
        Long userId = findUserIdByEmail(email);
        if (userId == null) {
            return buildExperience(null);
        }
        return buildExperience(userActivityRepository.findById(userId).orElse(null));
    }

    public Map<String, Object> awardMovieWatchExp(String email, Long episodeId) {
        return awardMovieWatchExp(email, episodeId, null);
    }

    public Map<String, Object> awardMovieWatchExp(String email, Long episodeId, Long movieId) {
        Long userId = findUserIdByEmail(email);
        if (userId == null) {
            return buildReward(false, null, 0, "User not found");
        }
        Long resolvedEpisodeId = episodeId;
        if (resolvedEpisodeId == null || resolvedEpisodeId <= 0) {
            resolvedEpisodeId = resolveLatestEpisodeId(userId, movieId);
        }
        if (resolvedEpisodeId == null || resolvedEpisodeId <= 0) {
            return buildReward(false, userActivityRepository.findById(userId).orElse(null), 0, "episodeId is required");
        }
        try {
            UserExpEntity before = userActivityRepository.findById(userId).orElse(null);
            userActivityRepository.addWatchEpisodeExp(userId, resolvedEpisodeId);
            UserExpEntity after = userActivityRepository.findById(userId).orElse(null);
            return buildReward(true, after, diffTotalExp(before, after), "Thêm EXP và Điểm thưởng xem phim thành công!");
        } catch (Exception ex) {
            return buildReward(false, userActivityRepository.findById(userId).orElse(null), 0, ex.getMessage());
        }
    }

    public Map<String, Object> awardChapterReadExp(String email, Long chapterId) {
        Long userId = findUserIdByEmail(email);
        if (userId == null) {
            return buildReward(false, null, 0, "User not found");
        }
        if (chapterId == null || chapterId <= 0) {
            return buildReward(false, userActivityRepository.findById(userId).orElse(null), 0, "chapterId is required");
        }
        try {
            UserExpEntity before = userActivityRepository.findById(userId).orElse(null);
            userActivityRepository.addReadChapterExp(userId, chapterId);
            UserExpEntity after = userActivityRepository.findById(userId).orElse(null);
            return buildReward(true, after, diffTotalExp(before, after), "Thêm EXP và Điểm thưởng đọc truyện thành công!");
        } catch (Exception ex) {
            return buildReward(false, userActivityRepository.findById(userId).orElse(null), 0, ex.getMessage());
        }
    }

    public boolean gainExpFromWatching(Long userId, Long episodeId) {
        if (userId == null || episodeId == null || episodeId <= 0) {
            return false;
        }
        try {
            userActivityRepository.addWatchEpisodeExp(userId, episodeId);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean gainExpFromReading(Long userId, Long chapterId) {
        if (userId == null || chapterId == null || chapterId <= 0) {
            return false;
        }
        try {
            userActivityRepository.addReadChapterExp(userId, chapterId);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private Long findUserIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(email.trim())
                .map(UserEntity::getId)
                .orElse(null);
    }

    private int diffTotalExp(UserExpEntity before, UserExpEntity after) {
        int beforeTotal = before == null || before.getTotalExp() == null ? 0 : before.getTotalExp();
        int afterTotal = after == null || after.getTotalExp() == null ? beforeTotal : after.getTotalExp();
        int afterMax = after == null || after.getMaxExp() == null ? 100 : after.getMaxExp();
        int levelDiff = 0;
        if (before != null && after != null && before.getCurrentLevel() != null && after.getCurrentLevel() != null) {
            levelDiff = Math.max(0, after.getCurrentLevel() - before.getCurrentLevel());
        }

        int gained = afterTotal - beforeTotal;
        if (gained < 0) {
            gained = afterTotal + (levelDiff > 0 ? Math.max(afterMax - 50, 0) : 0) - beforeTotal;
        }
        return Math.max(gained, 0);
    }

    private Long resolveLatestEpisodeId(Long userId, Long movieId) {
        if (userId == null || movieId == null || movieId <= 0) {
            return null;
        }
        try {
            return jdbcTemplate.query(
                    """
                    SELECT TOP 1 EpisodeID
                    FROM MovieHistory
                    WHERE UserID = ? AND MovieID = ? AND EpisodeID IS NOT NULL
                    ORDER BY WatchedAt DESC, HistoryID DESC
                    """,
                    rs -> rs.next() ? rs.getLong(1) : null,
                    userId,
                    movieId
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private Map<String, Object> buildExperience(UserExpEntity entity) {
        int totalExp = entity == null || entity.getTotalExp() == null ? 0 : entity.getTotalExp();
        int level = entity == null || entity.getCurrentLevel() == null || entity.getCurrentLevel() <= 0 ? 1 : entity.getCurrentLevel();
        int maxExp = entity == null || entity.getMaxExp() == null || entity.getMaxExp() <= 0 ? 100 : entity.getMaxExp();
        int expToNextLevel = Math.max(maxExp - totalExp, 0);
        return Map.of(
                "totalExp", totalExp,
                "level", level,
                "currentLevelExp", totalExp,
                "maxExp", maxExp,
                "expToNextLevel", expToNextLevel
        );
    }

    private Map<String, Object> buildReward(boolean ok, UserExpEntity entity, int expGained, String message) {
        int totalExp = entity == null || entity.getTotalExp() == null ? 0 : entity.getTotalExp();
        int level = entity == null || entity.getCurrentLevel() == null || entity.getCurrentLevel() <= 0 ? 1 : entity.getCurrentLevel();
        int maxExp = entity == null || entity.getMaxExp() == null || entity.getMaxExp() <= 0 ? 100 : entity.getMaxExp();
        int expToNextLevel = Math.max(maxExp - totalExp, 0);
        return Map.of(
                "ok", ok,
                "expGained", Math.max(expGained, 0),
                "totalExp", totalExp,
                "level", level,
                "currentLevelExp", totalExp,
                "maxExp", maxExp,
                "expToNextLevel", expToNextLevel,
                "message", message == null ? "" : message
        );
    }
}
