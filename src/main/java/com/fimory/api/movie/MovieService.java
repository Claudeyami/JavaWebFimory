package com.fimory.api.movie;

import com.fimory.api.common.NotFoundException;
import com.fimory.api.category.CategoryDto;
import com.fimory.api.domain.MovieEntity;
import com.fimory.api.domain.MovieEpisodeEntity;
import com.fimory.api.repository.MovieEpisodeRepository;
import com.fimory.api.repository.MovieRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.LinkedHashSet;

@Service
public class MovieService {

    private final MovieRepository movieRepository;
    private final MovieEpisodeRepository movieEpisodeRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Path uploadRoot;

    public MovieService(MovieRepository movieRepository,
                        MovieEpisodeRepository movieEpisodeRepository,
                        JdbcTemplate jdbcTemplate,
                        @Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.movieRepository = movieRepository;
        this.movieEpisodeRepository = movieEpisodeRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public List<MovieDto> getMovies() {
        String sql = """
                SELECT m.MovieID, m.Slug, m.Title, m.Description, m.PosterURL, m.Status, m.IsFree,
                       m.ViewCount, m.Rating, m.TrailerURL, m.ReleaseYear, m.Duration, m.Country,
                       m.Director, m.Cast, m.UploaderID, m.CreatedAt, m.UpdatedAt,
                       u.Username AS UploaderName, u.Email AS UploaderEmail, r.RoleName AS UploaderRole
                FROM Movies m
                LEFT JOIN Users u ON u.UserID = m.UploaderID
                LEFT JOIN Roles r ON r.RoleID = u.RoleID
                ORDER BY m.CreatedAt DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> toMovieDto(rs.getLong("MovieID"), rs));
    }

    public MovieDto getMovieBySlug(String slug) {
        String sql = """
                SELECT TOP 1 m.MovieID, m.Slug, m.Title, m.Description, m.PosterURL, m.Status, m.IsFree,
                             m.ViewCount, m.Rating, m.TrailerURL, m.ReleaseYear, m.Duration, m.Country,
                             m.Director, m.Cast, m.UploaderID, m.CreatedAt, m.UpdatedAt,
                             u.Username AS UploaderName, u.Email AS UploaderEmail, r.RoleName AS UploaderRole
                FROM Movies m
                LEFT JOIN Users u ON u.UserID = m.UploaderID
                LEFT JOIN Roles r ON r.RoleID = u.RoleID
                WHERE m.Slug = ?
                """;
        List<MovieDto> results = jdbcTemplate.query(sql, (rs, rowNum) -> toMovieDto(rs.getLong("MovieID"), rs), slug);
        if (results.isEmpty()) {
            throw new NotFoundException("Movie not found");
        }
        return results.getFirst();
    }

    public List<MovieEpisodeDto> getEpisodesBySlug(String slug) {
        MovieEntity movie = movieRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Movie not found"));

        return movieEpisodeRepository.findByMovieIdOrderByEpisodeNumberAsc(movie.getId()).stream()
                .map(episode -> new MovieEpisodeDto(
                        episode.getId(),
                        episode.getEpisodeNumber(),
                        episode.getTitle(),
                        normalizeVideoPath(episode.getVideoUrl()),
                        episode.getDuration(),
                        episode.getViewCount() == null ? 0L : episode.getViewCount()
                ))
                .toList();
    }

    public List<MovieEpisodeDto> getEpisodesByMovieId(Long movieId) {
        return movieEpisodeRepository.findByMovieIdOrderByEpisodeNumberAsc(movieId).stream()
                .map(episode -> new MovieEpisodeDto(
                        episode.getId(),
                        episode.getEpisodeNumber(),
                        episode.getTitle(),
                        normalizeVideoPath(episode.getVideoUrl()),
                        episode.getDuration(),
                        episode.getViewCount() == null ? 0L : episode.getViewCount()
                ))
                .toList();
    }

    public List<MovieDto> getMoviesByCategory(Long categoryId) {
        String sql = """
                SELECT m.MovieID, m.Slug, m.Title, m.Description, m.PosterURL, m.Status, m.IsFree,
                       m.ViewCount, m.Rating, m.TrailerURL, m.ReleaseYear, m.Duration, m.Country,
                       m.Director, m.Cast, m.UploaderID, m.CreatedAt, m.UpdatedAt,
                       u.Username AS UploaderName, u.Email AS UploaderEmail, r.RoleName AS UploaderRole
                FROM Movies m
                INNER JOIN MovieCategories mc ON mc.MovieID = m.MovieID
                LEFT JOIN Users u ON u.UserID = m.UploaderID
                LEFT JOIN Roles r ON r.RoleID = u.RoleID
                WHERE mc.CategoryID = ?
                ORDER BY m.CreatedAt DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> toMovieDto(rs.getLong("MovieID"), rs), categoryId);
    }

    public List<CategoryDto> getCategoriesByMovie(Long movieId) {
        String nameColumn = resolveCategoryNameColumn();
        String sql = "SELECT c.CategoryID, c." + nameColumn + " AS CategoryName, c.Slug " +
                "FROM Categories c INNER JOIN MovieCategories mc ON mc.CategoryID = c.CategoryID " +
                "WHERE mc.MovieID = ? ORDER BY c.CategoryID";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CategoryDto(
                rs.getLong("CategoryID"),
                rs.getString("CategoryName"),
                rs.getString("Slug")
        ), movieId);
    }

    public List<Map<String, String>> getTagsByMovie(Long movieId) {
        String sql = """
                SELECT t.TagID, t.TagName, t.Slug
                FROM Tags t
                INNER JOIN MovieTags mt ON mt.TagID = t.TagID
                WHERE mt.MovieID = ?
                ORDER BY t.TagID
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> Map.of(
                "TagID", String.valueOf(rs.getLong("TagID")),
                "Name", rs.getString("TagName"),
                "Slug", rs.getString("Slug")
        ), movieId);
    }

    @Transactional
    public MovieDto createMovie(MovieUpsertRequest request) {
        return createMovie(request, null);
    }

    @Transactional
    public MovieDto createMovie(MovieUpsertRequest request, Long uploaderId) {
        return createMovie(request, uploaderId, null);
    }

    @Transactional
    public MovieDto createMovie(MovieUpsertRequest request, Long uploaderId, List<Long> categoryIds) {
        MovieEntity entity = new MovieEntity();
        apply(entity, request, uploaderId);
        MovieEntity saved = movieRepository.save(entity);
        replaceMovieCategories(saved.getId(), categoryIds);
        return map(saved);
    }

    @Transactional
    public MovieDto updateMovie(Long movieId, MovieUpsertRequest request) {
        return updateMovie(movieId, request, null);
    }

    @Transactional
    public MovieDto updateMovie(Long movieId, MovieUpsertRequest request, List<Long> categoryIds) {
        MovieEntity entity = movieRepository.findById(movieId)
                .orElseThrow(() -> new NotFoundException("Movie not found"));
        String previousCoverUrl = entity.getCoverUrl();
        apply(entity, request, entity.getUploaderId());
        MovieEntity saved = movieRepository.save(entity);
        if (categoryIds != null) {
            replaceMovieCategories(movieId, categoryIds);
        }
        if (request.coverUrl() != null && !request.coverUrl().isBlank()
                && previousCoverUrl != null && !previousCoverUrl.isBlank()
                && !previousCoverUrl.equals(saved.getCoverUrl())) {
            deleteStoredMediaFileQuietly(previousCoverUrl);
        }
        return map(saved);
    }

    @Transactional
    public void deleteMovie(Long movieId) {
        List<String> mediaToDelete = new ArrayList<>();
        mediaToDelete.addAll(jdbcTemplate.query(
                "SELECT PosterURL FROM Movies WHERE MovieID = ?",
                (rs, rowNum) -> rs.getString("PosterURL"),
                movieId
        ));
        mediaToDelete.addAll(jdbcTemplate.query(
                "SELECT VideoURL FROM MovieEpisodes WHERE MovieID = ?",
                (rs, rowNum) -> rs.getString("VideoURL"),
                movieId
        ));

        jdbcTemplate.update("DELETE FROM MovieHistory WHERE MovieID = ?", movieId);
        jdbcTemplate.update(
                """
                DELETE FROM MovieHistory
                WHERE EpisodeID IN (SELECT EpisodeID FROM MovieEpisodes WHERE MovieID = ?)
                """,
                movieId
        );
        if (tableExists("MovieServers")) {
            jdbcTemplate.update(
                    """
                    DELETE FROM MovieServers
                    WHERE EpisodeID IN (SELECT EpisodeID FROM MovieEpisodes WHERE MovieID = ?)
                    """,
                    movieId
            );
        }
        jdbcTemplate.update("DELETE FROM MovieEpisodes WHERE MovieID = ?", movieId);
        jdbcTemplate.update("DELETE FROM MovieCategories WHERE MovieID = ?", movieId);
        jdbcTemplate.update("DELETE FROM MovieTags WHERE MovieID = ?", movieId);
        jdbcTemplate.update("DELETE FROM MovieRatings WHERE MovieID = ?", movieId);
        jdbcTemplate.update("DELETE FROM MovieFavorites WHERE MovieID = ?", movieId);
        jdbcTemplate.update("DELETE FROM MovieComments WHERE MovieID = ?", movieId);
        if (tableExists("RelatedMovies")) {
            jdbcTemplate.update("DELETE FROM RelatedMovies WHERE MovieID = ? OR RelatedMovieID = ?", movieId, movieId);
        }
        movieRepository.deleteById(movieId);

        mediaToDelete.forEach(this::deleteStoredMediaFileQuietly);
    }

    @Transactional
    public MovieEpisodeDto addEpisode(Long movieId, String title, String videoUrl) {
        int nextEpisode = movieEpisodeRepository.findByMovieIdOrderByEpisodeNumberAsc(movieId).stream()
                .map(MovieEpisodeEntity::getEpisodeNumber)
                .filter(number -> number != null && number > 0)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;

        MovieEpisodeEntity entity = new MovieEpisodeEntity();
        entity.setMovieId(movieId);
        entity.setEpisodeNumber(nextEpisode);
        entity.setTitle((title == null || title.isBlank()) ? ("Episode " + nextEpisode) : title);
        entity.setVideoUrl(videoUrl);

        MovieEpisodeEntity saved = movieEpisodeRepository.save(entity);
        return new MovieEpisodeDto(
                saved.getId(),
                saved.getEpisodeNumber(),
                saved.getTitle(),
                saved.getVideoUrl(),
                saved.getDuration(),
                saved.getViewCount() == null ? 0L : saved.getViewCount()
        );
    }

    @Transactional
    public void replaceMovieCategories(Long movieId, List<Long> categoryIds) {
        jdbcTemplate.update("DELETE FROM MovieCategories WHERE MovieID = ?", movieId);
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        for (Long categoryId : new LinkedHashSet<>(categoryIds)) {
            if (categoryId == null) {
                continue;
            }
            jdbcTemplate.update(
                    "INSERT INTO MovieCategories (MovieID, CategoryID) VALUES (?, ?)",
                    movieId,
                    categoryId
            );
        }
    }

    @Transactional
    public MovieEpisodeDto updateEpisode(Long episodeId, String title, String videoUrl) {
        MovieEpisodeEntity entity = movieEpisodeRepository.findById(episodeId)
                .orElseThrow(() -> new NotFoundException("Episode not found"));
        String previousVideoUrl = entity.getVideoUrl();
        if (title != null && !title.isBlank()) {
            entity.setTitle(title);
        }
        if (videoUrl != null && !videoUrl.isBlank()) {
            entity.setVideoUrl(videoUrl);
        }
        MovieEpisodeEntity saved = movieEpisodeRepository.save(entity);
        MovieEpisodeDto dto = new MovieEpisodeDto(
                saved.getId(),
                saved.getEpisodeNumber(),
                saved.getTitle(),
                saved.getVideoUrl(),
                saved.getDuration(),
                saved.getViewCount() == null ? 0L : saved.getViewCount()
        );
        if (videoUrl != null && !videoUrl.isBlank() && previousVideoUrl != null && !previousVideoUrl.isBlank()
                && !previousVideoUrl.equals(saved.getVideoUrl())) {
            deleteStoredMediaFileQuietly(previousVideoUrl);
        }
        return dto;
    }

    @Transactional
    public void deleteEpisode(Long episodeId) {
        MovieEpisodeEntity episode = movieEpisodeRepository.findById(episodeId)
                .orElseThrow(() -> new NotFoundException("Episode not found"));
        jdbcTemplate.update("DELETE FROM MovieHistory WHERE EpisodeID = ?", episodeId);
        if (tableExists("MovieServers")) {
            jdbcTemplate.update("DELETE FROM MovieServers WHERE EpisodeID = ?", episodeId);
        }
        movieEpisodeRepository.deleteById(episodeId);
        deleteStoredMediaFileQuietly(episode.getVideoUrl());
    }

    @Transactional
    public void updateMovieStatus(Long movieId, String status) {
        movieRepository.findById(movieId)
                .orElseThrow(() -> new NotFoundException("Movie not found"));
        String resolvedStatus = (status == null || status.isBlank()) ? "Pending" : status;
        boolean isApproved = "approved".equalsIgnoreCase(resolvedStatus);
        jdbcTemplate.update(
                "UPDATE Movies SET Status = ?, IsApproved = ?, ApprovedAt = CASE WHEN ? = 1 THEN GETDATE() ELSE ApprovedAt END WHERE MovieID = ?",
                resolvedStatus,
                isApproved,
                isApproved ? 1 : 0,
                movieId
        );
    }

    @Transactional
    public int autoApprovePendingMovies() {
        return jdbcTemplate.update(
                """
                UPDATE Movies
                SET Status = 'Approved', IsApproved = 1, ApprovedAt = COALESCE(ApprovedAt, GETDATE())
                WHERE Status IS NULL OR LTRIM(RTRIM(Status)) = '' OR LOWER(LTRIM(RTRIM(Status))) = 'pending'
                """
        );
    }

    private void apply(MovieEntity entity, MovieUpsertRequest request, Long uploaderId) {
        entity.setSlug(ensureUniqueSlug(request.slug(), entity.getId()));
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setCoverUrl(request.coverUrl());
        if (uploaderId != null) {
            entity.setUploaderId(uploaderId);
        }
        if (entity.getUploaderId() == null) {
            throw new IllegalArgumentException("UploaderID is required");
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            entity.setStatus("Pending");
        }
        if (entity.getIsFree() == null) {
            entity.setIsFree(Boolean.FALSE);
        }
    }

    private String ensureUniqueSlug(String requestedSlug, Long currentMovieId) {
        if (requestedSlug == null || requestedSlug.isBlank()) {
            throw new IllegalArgumentException("Slug is required");
        }

        String baseSlug = requestedSlug.trim();
        String candidate = baseSlug;
        int suffix = 2;

        while (true) {
            var existing = movieRepository.findBySlug(candidate);
            if (existing.isEmpty()) {
                return candidate;
            }
            if (currentMovieId != null && currentMovieId.equals(existing.get().getId())) {
                return candidate;
            }
            candidate = baseSlug + "-" + suffix;
            suffix++;
        }
    }

    private MovieDto map(MovieEntity movie) {
        return new MovieDto(movie.getId(), movie.getSlug(), movie.getTitle(), movie.getDescription(), normalizePosterPath(movie.getCoverUrl()));
    }

    private MovieDto toMovieDto(Long movieId, java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MovieDto(
                movieId,
                rs.getString("Slug"),
                rs.getString("Title"),
                rs.getString("Description"),
                normalizePosterPath(rs.getString("PosterURL")),
                rs.getString("Status") == null ? "Approved" : rs.getString("Status"),
                rs.getObject("IsFree") != null && rs.getBoolean("IsFree"),
                rs.getObject("ViewCount") != null ? rs.getLong("ViewCount") : 0L,
                rs.getObject("Rating") != null ? rs.getDouble("Rating") : 0.0,
                rs.getString("TrailerURL"),
                rs.getObject("ReleaseYear") != null ? rs.getInt("ReleaseYear") : null,
                rs.getObject("Duration") != null ? rs.getInt("Duration") : null,
                rs.getString("Country"),
                rs.getString("Director"),
                rs.getString("Cast"),
                rs.getObject("UploaderID") != null ? rs.getLong("UploaderID") : null,
                rs.getString("UploaderName"),
                rs.getString("UploaderEmail"),
                rs.getString("UploaderRole"),
                rs.getObject("CreatedAt") != null ? String.valueOf(rs.getObject("CreatedAt")) : null,
                rs.getObject("UpdatedAt") != null ? String.valueOf(rs.getObject("UpdatedAt")) : null
        );
    }

    private String resolveCategoryNameColumn() {
        Integer hasCategoryName = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(1)
                        FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_NAME = 'Categories' AND COLUMN_NAME = 'CategoryName'
                        """,
                Integer.class
        );
        if (hasCategoryName != null && hasCategoryName > 0) {
            return "CategoryName";
        }
        return "Name";
    }

    private String normalizePosterPath(String value) {
        return normalizeMediaPath(value, "posters");
    }

    private String normalizeVideoPath(String value) {
        return normalizeMediaPath(value, "videos");
    }

    private String normalizeMediaPath(String value, String defaultFolder) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value.trim().replace("\\", "/");
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        if (normalized.startsWith("/storage/") || normalized.startsWith("/uploads/")) {
            return normalized;
        }
        if (normalized.startsWith("storage/") || normalized.startsWith("uploads/")) {
            return "/" + normalized;
        }
        return "/storage/" + defaultFolder + "/" + normalized;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private void deleteStoredMediaFileQuietly(String rawPath) {
        Path filePath = resolveStoredFilePath(rawPath);
        if (filePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(filePath);
        } catch (Exception ignored) {
        }
    }

    private Path resolveStoredFilePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String normalized = rawPath.trim().replace("\\", "/");
        int queryPos = normalized.indexOf('?');
        if (queryPos >= 0) {
            normalized = normalized.substring(0, queryPos);
        }
        int fragmentPos = normalized.indexOf('#');
        if (fragmentPos >= 0) {
            normalized = normalized.substring(0, fragmentPos);
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return null;
        }

        try {
            Path absolute = Path.of(normalized).toAbsolutePath().normalize();
            if (absolute.startsWith(uploadRoot)) {
                return absolute;
            }
        } catch (Exception ignored) {
        }

        String relative = normalized;
        if (relative.startsWith("/storage/")) {
            relative = relative.substring("/storage/".length());
        } else if (relative.startsWith("storage/")) {
            relative = relative.substring("storage/".length());
        } else if (relative.startsWith("/uploads/")) {
            relative = relative.substring("/uploads/".length());
        } else if (relative.startsWith("uploads/")) {
            relative = relative.substring("uploads/".length());
        } else if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }

        Path resolved = uploadRoot.resolve(relative).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            return null;
        }
        return resolved;
    }
}
