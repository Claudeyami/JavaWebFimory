package com.fimory.api.history;

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
@RequestMapping("/history")
public class HistoryController {

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    public HistoryController(CurrentUserProvider currentUserProvider,
                             UserRepository userRepository,
                             JdbcTemplate jdbcTemplate) {
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/movie")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addMovieHistory(@RequestBody(required = false) Map<String, Object> payload,
                                                                             @RequestParam(required = false) String email,
                                                                             @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        Long userId = resolveUserId(payload, email, emailHeader);
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("saved", false, "message", "Missing user context")));
        }
        Long movieId = asLong(payload == null ? null : payload.get("movieId"));
        if (movieId == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, Map.of("message", "movieId is required"), Map.of()));
        }
        Long episodeId = asLong(payload == null ? null : payload.get("episodeId"));
        int updated = jdbcTemplate.update(
                """
                UPDATE MovieHistory
                SET EpisodeID = ?, WatchedAt = GETDATE()
                WHERE HistoryID = (
                    SELECT TOP 1 HistoryID
                    FROM MovieHistory
                    WHERE UserID = ? AND MovieID = ?
                    ORDER BY WatchedAt DESC, HistoryID DESC
                )
                """,
                episodeId, userId, movieId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO MovieHistory (UserID, MovieID, EpisodeID, WatchedAt) VALUES (?, ?, ?, GETDATE())",
                    userId, movieId, episodeId
            );
        }
        jdbcTemplate.update(
                "UPDATE Movies SET ViewCount = COALESCE(ViewCount, 0) + 1 WHERE MovieID = ?",
                movieId
        );
        if (episodeId != null) {
            jdbcTemplate.update(
                    "UPDATE MovieEpisodes SET ViewCount = COALESCE(ViewCount, 0) + 1 WHERE EpisodeID = ?",
                    episodeId
            );
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("saved", true, "movieId", movieId, "episodeId", episodeId)));
    }

    @GetMapping("/movie")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMovieHistory(@RequestParam(required = false) String email,
                                                                                   @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        Long userId = resolveUserId(null, email, emailHeader);
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        String sql = """
                WITH ranked AS (
                    SELECT h.*,
                           ROW_NUMBER() OVER (
                               PARTITION BY h.MovieID
                               ORDER BY h.WatchedAt DESC, h.HistoryID DESC
                           ) AS rn
                    FROM MovieHistory h
                    WHERE h.UserID = ?
                )
                SELECT h.HistoryID, h.MovieID, h.EpisodeID, h.WatchedAt,
                       m.Title, m.Slug, m.PosterURL,
                       e.EpisodeNumber, e.Title AS EpisodeTitle
                FROM ranked h
                LEFT JOIN Movies m ON m.MovieID = h.MovieID
                LEFT JOIN MovieEpisodes e ON e.EpisodeID = h.EpisodeID
                WHERE h.rn = 1
                ORDER BY h.WatchedAt DESC
                """;
        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("HistoryID", rs.getLong("HistoryID"));
            item.put("MovieID", rs.getLong("MovieID"));
            item.put("EpisodeID", rs.getObject("EpisodeID"));
            item.put("WatchedAt", rs.getObject("WatchedAt"));
            item.put("Title", rs.getString("Title"));
            item.put("Slug", rs.getString("Slug"));
            item.put("PosterURL", rs.getString("PosterURL"));
            item.put("EpisodeNumber", rs.getObject("EpisodeNumber"));
            item.put("EpisodeTitle", rs.getString("EpisodeTitle"));
            return item;
        }, userId);
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @DeleteMapping("/movie")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearMovieHistory(@RequestParam(required = false) String email,
                                                                               @RequestParam(required = false) Long historyId,
                                                                               @RequestParam(required = false) Long movieId,
                                                                               @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        Long userId = resolveUserId(null, email, emailHeader);
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", false, "count", 0)));
        }
        int deleted;
        if (historyId != null) {
            deleted = jdbcTemplate.update(
                    "DELETE FROM MovieHistory WHERE UserID = ? AND HistoryID = ?",
                    userId,
                    historyId
            );
        } else if (movieId != null) {
            deleted = jdbcTemplate.update(
                    "DELETE FROM MovieHistory WHERE UserID = ? AND MovieID = ?",
                    userId,
                    movieId
            );
        } else {
            deleted = jdbcTemplate.update("DELETE FROM MovieHistory WHERE UserID = ?", userId);
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true, "count", deleted)));
    }

    @PostMapping("/series")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addSeriesHistory(@RequestBody(required = false) Map<String, Object> payload,
                                                                              @RequestParam(required = false) String email,
                                                                              @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        Long userId = resolveUserId(payload, email, emailHeader);
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("saved", false, "message", "Missing user context")));
        }
        Long seriesId = asLong(payload == null ? null : payload.get("seriesId"));
        if (seriesId == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, Map.of("message", "seriesId is required"), Map.of()));
        }
        Long chapterId = asLong(payload == null ? null : payload.get("chapterId"));
        int updated = jdbcTemplate.update(
                """
                UPDATE SeriesHistory
                SET ChapterID = ?, ReadAt = GETDATE()
                WHERE HistoryID = (
                    SELECT TOP 1 HistoryID
                    FROM SeriesHistory
                    WHERE UserID = ? AND SeriesID = ?
                    ORDER BY ReadAt DESC, HistoryID DESC
                )
                """,
                chapterId, userId, seriesId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO SeriesHistory (UserID, SeriesID, ChapterID, ReadAt) VALUES (?, ?, ?, GETDATE())",
                    userId, seriesId, chapterId
            );
        }
        jdbcTemplate.update(
                "UPDATE Series SET ViewCount = COALESCE(ViewCount, 0) + 1 WHERE SeriesID = ?",
                seriesId
        );
        if (chapterId != null) {
            jdbcTemplate.update(
                    "UPDATE Chapters SET ViewCount = COALESCE(ViewCount, 0) + 1 WHERE ChapterID = ?",
                    chapterId
            );
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("saved", true, "seriesId", seriesId, "chapterId", chapterId)));
    }

    @GetMapping("/series")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSeriesHistory(@RequestParam(required = false) String email,
                                                                                    @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        Long userId = resolveUserId(null, email, emailHeader);
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
        String sql = """
                WITH ranked AS (
                    SELECT h.*,
                           ROW_NUMBER() OVER (
                               PARTITION BY h.SeriesID
                               ORDER BY h.ReadAt DESC, h.HistoryID DESC
                           ) AS rn
                    FROM SeriesHistory h
                    WHERE h.UserID = ?
                )
                SELECT h.HistoryID, h.SeriesID, h.ChapterID, h.ReadAt,
                       s.Title, s.Slug, s.CoverURL,
                       c.ChapterNumber, c.Title AS ChapterTitle
                FROM ranked h
                LEFT JOIN Series s ON s.SeriesID = h.SeriesID
                LEFT JOIN Chapters c ON c.ChapterID = h.ChapterID
                WHERE h.rn = 1
                ORDER BY h.ReadAt DESC
                """;
        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("HistoryID", rs.getLong("HistoryID"));
            item.put("SeriesID", rs.getLong("SeriesID"));
            item.put("ChapterID", rs.getObject("ChapterID"));
            item.put("ReadAt", rs.getObject("ReadAt"));
            item.put("Title", rs.getString("Title"));
            item.put("Slug", rs.getString("Slug"));
            item.put("CoverURL", rs.getString("CoverURL"));
            item.put("ChapterNumber", rs.getObject("ChapterNumber"));
            item.put("ChapterTitle", rs.getString("ChapterTitle"));
            return item;
        }, userId);
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @DeleteMapping("/series")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearSeriesHistory(@RequestParam(required = false) String email,
                                                                                @RequestParam(required = false) Long historyId,
                                                                                @RequestParam(required = false) Long seriesId,
                                                                                @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        Long userId = resolveUserId(null, email, emailHeader);
        if (userId == null) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", false, "count", 0)));
        }
        int deleted;
        if (historyId != null) {
            deleted = jdbcTemplate.update(
                    "DELETE FROM SeriesHistory WHERE UserID = ? AND HistoryID = ?",
                    userId,
                    historyId
            );
        } else if (seriesId != null) {
            deleted = jdbcTemplate.update(
                    "DELETE FROM SeriesHistory WHERE UserID = ? AND SeriesID = ?",
                    userId,
                    seriesId
            );
        } else {
            deleted = jdbcTemplate.update("DELETE FROM SeriesHistory WHERE UserID = ?", userId);
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true, "count", deleted)));
    }

    private Long resolveUserId(Map<String, Object> payload, String emailQuery, String emailHeader) {
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
            AuthenticatedUser user = currentUserProvider.requireUser();
            return user.userId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String raw = String.valueOf(value).trim();
        if (raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
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
}
