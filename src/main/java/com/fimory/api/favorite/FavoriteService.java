package com.fimory.api.favorite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FavoriteService {

    private final JdbcTemplate jdbcTemplate;

    public FavoriteService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Long> getMovieFavorites(String email) {
        Long userId = resolveUserId(email);
        if (userId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT MovieID FROM MovieFavorites WHERE UserID = ? ORDER BY FavoriteID DESC",
                (rs, rowNum) -> rs.getLong("MovieID"),
                userId
        );
    }

    public List<Long> getSeriesFavorites(String email) {
        Long userId = resolveUserId(email);
        if (userId == null) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT SeriesID FROM SeriesFavorites WHERE UserID = ? ORDER BY FavoriteID DESC",
                (rs, rowNum) -> rs.getLong("SeriesID"),
                userId
        );
    }

    public void addMovieFavorite(String email, Long movieId) {
        Long userId = resolveUserId(email);
        if (userId == null || movieId == null) {
            return;
        }
        jdbcTemplate.update(
                """
                IF NOT EXISTS (SELECT 1 FROM MovieFavorites WHERE UserID = ? AND MovieID = ?)
                BEGIN
                    INSERT INTO MovieFavorites (UserID, MovieID, CreatedAt) VALUES (?, ?, GETDATE())
                END
                """,
                userId, movieId, userId, movieId
        );
    }

    public void removeMovieFavorite(String email, Long movieId) {
        Long userId = resolveUserId(email);
        if (userId == null || movieId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM MovieFavorites WHERE UserID = ? AND MovieID = ?", userId, movieId);
    }

    public boolean isMovieFavorite(String email, Long movieId) {
        Long userId = resolveUserId(email);
        if (userId == null || movieId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM MovieFavorites WHERE UserID = ? AND MovieID = ?",
                Integer.class,
                userId,
                movieId
        );
        return count != null && count > 0;
    }

    public void addSeriesFavorite(String email, Long seriesId) {
        Long userId = resolveUserId(email);
        if (userId == null || seriesId == null) {
            return;
        }
        jdbcTemplate.update(
                """
                IF NOT EXISTS (SELECT 1 FROM SeriesFavorites WHERE UserID = ? AND SeriesID = ?)
                BEGIN
                    INSERT INTO SeriesFavorites (UserID, SeriesID, CreatedAt) VALUES (?, ?, GETDATE())
                END
                """,
                userId, seriesId, userId, seriesId
        );
    }

    public void removeSeriesFavorite(String email, Long seriesId) {
        Long userId = resolveUserId(email);
        if (userId == null || seriesId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM SeriesFavorites WHERE UserID = ? AND SeriesID = ?", userId, seriesId);
    }

    public boolean isSeriesFavorite(String email, Long seriesId) {
        Long userId = resolveUserId(email);
        if (userId == null || seriesId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM SeriesFavorites WHERE UserID = ? AND SeriesID = ?",
                Integer.class,
                userId,
                seriesId
        );
        return count != null && count > 0;
    }

    private Long resolveUserId(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return jdbcTemplate.query(
                "SELECT TOP 1 UserID FROM Users WHERE LOWER(Email) = LOWER(?)",
                rs -> rs.next() ? rs.getLong(1) : null,
                email.trim()
        );
    }
}
