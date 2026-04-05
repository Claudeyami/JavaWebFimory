package com.fimory.api.admin;

import com.fimory.api.category.CategoryDto;
import com.fimory.api.category.CategoryService;
import com.fimory.api.category.CategoryUpsertRequest;
import com.fimory.api.crawl.CrawlStoryService;
import com.fimory.api.common.ApiResponse;
import com.fimory.api.common.NotFoundException;
import com.fimory.api.common.UnauthorizedException;
import com.fimory.api.movie.MovieDto;
import com.fimory.api.movie.MovieService;
import com.fimory.api.movie.MovieUpsertRequest;
import com.fimory.api.security.AuthenticatedUser;
import com.fimory.api.security.CurrentUserProvider;
import com.fimory.api.series.SeriesDto;
import com.fimory.api.series.SeriesService;
import com.fimory.api.series.SeriesUpsertRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final MovieService movieService;
    private final SeriesService seriesService;
    private final CategoryService categoryService;
    private final AdminUserService adminUserService;
    private final CurrentUserProvider currentUserProvider;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CrawlStoryService crawlStoryService;
    private final String uploadDir;

    public AdminController(MovieService movieService,
                           SeriesService seriesService,
                           CategoryService categoryService,
                           AdminUserService adminUserService,
                           CurrentUserProvider currentUserProvider,
                           JdbcTemplate jdbcTemplate,
                           ObjectMapper objectMapper,
                           CrawlStoryService crawlStoryService,
                           @Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.movieService = movieService;
        this.seriesService = seriesService;
        this.categoryService = categoryService;
        this.adminUserService = adminUserService;
        this.currentUserProvider = currentUserProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.crawlStoryService = crawlStoryService;
        this.uploadDir = uploadDir;
    }

    @GetMapping("/movies")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<List<MovieDto>>> adminMovies() {
        return ResponseEntity.ok(ApiResponse.ok(movieService.getMovies()));
    }

    @PostMapping("/movies")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<MovieDto>> createMovie(@Valid @RequestBody MovieUpsertRequest request) {
        Long uploaderId = currentUserProvider.requireUser().userId();
        MovieDto created = movieService.createMovie(request, uploaderId);
        movieService.updateMovieStatus(created.id(), "Approved");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PostMapping(value = "/movies", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<MovieDto>> createMovieForm(@RequestParam String title,
                                                                 @RequestParam(required = false) String description,
                                                                 @RequestParam(required = false) String slug,
                                                                 @RequestParam(required = false) String posterLocal,
                                                                 @RequestParam(required = false) List<Long> categoryIds,
                                                                 @RequestParam(required = false) String episodes,
                                                                 @RequestPart(required = false) MultipartFile coverImage,
                                                                 @RequestPart(required = false) MultipartFile videoFile,
                                                                 @RequestPart(required = false) List<MultipartFile> episodeFiles) {
        String resolvedSlug = (slug == null || slug.isBlank()) ? slugify(title) : slugify(slug);
        String coverUrl = (coverImage != null && !coverImage.isEmpty())
                ? storeFile(coverImage, "posters")
                : normalizeIncomingPath(posterLocal);
        Long uploaderId = currentUserProvider.requireUser().userId();
        List<EpisodeMeta> episodeMetas = parseEpisodeMeta(episodes);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(createMovieWithEpisodes(
                        new MovieUpsertRequest(resolvedSlug, title, description, coverUrl),
                        uploaderId,
                        categoryIds,
                        videoFile,
                        episodeFiles,
                        episodeMetas
                )));
    }

    @PutMapping("/movies/{movieId}")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<MovieDto>> updateMovie(@PathVariable Long movieId,
                                                             @Valid @RequestBody MovieUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(movieService.updateMovie(movieId, request)));
    }

    @PutMapping(value = "/movies/{movieId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<MovieDto>> updateMovieForm(@PathVariable Long movieId,
                                                                 @RequestParam(required = false) String title,
                                                                 @RequestParam(required = false) String description,
                                                                 @RequestParam(required = false) String slug,
                                                                 @RequestParam(required = false) List<Long> categoryIds,
                                                                 @RequestPart(required = false) MultipartFile coverImage,
                                                                 @RequestPart(required = false) MultipartFile videoFile) {
        MovieDto current = movieService.getMovies().stream()
                .filter(m -> m.id().equals(movieId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Movie not found"));
        String resolvedTitle = (title == null || title.isBlank()) ? current.title() : title;
        String resolvedDescription = description != null ? description : current.description();
        String resolvedSlug = (slug == null || slug.isBlank()) ? current.slug() : slugify(slug);
        String coverUrl = (coverImage != null && !coverImage.isEmpty())
                ? storeFile(coverImage, "posters")
                : current.coverUrl();
        return ResponseEntity.ok(ApiResponse.ok(
                movieService.updateMovie(movieId, new MovieUpsertRequest(resolvedSlug, resolvedTitle, resolvedDescription, coverUrl), categoryIds)
        ));
    }

    @DeleteMapping("/movies/{movieId}")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteMovie(@PathVariable Long movieId) {
        movieService.deleteMovie(movieId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true)));
    }

    @GetMapping("/stories")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<List<SeriesDto>>> adminStories() {
        return ResponseEntity.ok(ApiResponse.ok(seriesService.getStories()));
    }

    @PostMapping("/stories")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<SeriesDto>> createStory(@Valid @RequestBody SeriesUpsertRequest request) {
        Long uploaderId = currentUserProvider.requireUser().userId();
        SeriesDto created = seriesService.createStory(request, uploaderId, null);
        markSeriesApproved(created.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(seriesService.getStoryBySlug(created.slug())));
    }

    @PostMapping(value = "/stories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createStoryForm(@RequestParam String title,
                                                                             @RequestParam(required = false) String description,
                                                                             @RequestParam(required = false) String slug,
                                                                             @RequestParam(required = false) String coverLocal,
                                                                             @RequestParam(required = false) String author,
                                                                             @RequestParam(required = false) String storyType,
                                                                             @RequestParam(required = false) Boolean isFree,
                                                                             @RequestParam(required = false) String crawledImages,
                                                                             @RequestParam(required = false) String chapters,
                                                                             @RequestParam(required = false) List<Long> categoryIds,
                                                                             @RequestPart(required = false) MultipartFile coverImage,
                                                                             @RequestPart(required = false) List<MultipartFile> contentFiles,
                                                                             @RequestPart(required = false) List<MultipartFile> chapterImages) {
        String resolvedSlug = (slug == null || slug.isBlank()) ? slugify(title) : slugify(slug);
        String coverUrl = (coverImage != null && !coverImage.isEmpty())
                ? storeFile(coverImage, "covers")
                : normalizeIncomingPath(coverLocal);
        if ((coverImage == null || coverImage.isEmpty()) && coverUrl != null && (coverUrl.startsWith("http://") || coverUrl.startsWith("https://"))) {
            String downloadedCover = crawlStoryService.ensureCoverStoredLocally(coverUrl, coverUrl);
            if (downloadedCover != null && !downloadedCover.isBlank()) {
                coverUrl = downloadedCover;
            }
        }
        Long uploaderId = currentUserProvider.requireUser().userId();
        SeriesDto created = seriesService.createStory(new SeriesUpsertRequest(resolvedSlug, title, description, coverUrl), uploaderId, categoryIds);
        markSeriesApproved(created.id());

        jdbcTemplate.update(
                """
                UPDATE Series
                SET Author = COALESCE(?, Author),
                    StoryType = COALESCE(?, StoryType),
                    IsFree = COALESCE(?, IsFree)
                WHERE SeriesID = ?
                """,
                author,
                storyType,
                isFree,
                created.id()
        );

        int chapterCount = createInitialStoryChapters(
                created.id(),
                storyType,
                contentFiles,
                chapterImages,
                crawledImages,
                chapters
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("SeriesID", created.id());
        payload.put("StoryID", created.id());
        payload.put("seriesId", created.id());
        payload.put("storyId", created.id());
        payload.put("chapterCount", chapterCount);
        payload.put("Title", created.title());
        payload.put("Slug", created.slug());
        payload.put("CoverURL", created.coverUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(payload));
    }

    @PutMapping("/stories/{seriesId}")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<SeriesDto>> updateStory(@PathVariable Long seriesId,
                                                              @Valid @RequestBody SeriesUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(seriesService.updateStory(seriesId, request, null)));
    }

    @PutMapping(value = "/stories/{seriesId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<SeriesDto>> updateStoryForm(@PathVariable Long seriesId,
                                                                  @RequestParam(required = false) String title,
                                                                  @RequestParam(required = false) String description,
                                                                  @RequestParam(required = false) String slug,
                                                                  @RequestParam(required = false) List<Long> categoryIds,
                                                                  @RequestPart(required = false) MultipartFile coverImage) {
        SeriesDto current = seriesService.getStories().stream()
                .filter(s -> s.id().equals(seriesId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Story not found"));
        String resolvedTitle = (title == null || title.isBlank()) ? current.title() : title;
        String resolvedDescription = description != null ? description : current.description();
        String resolvedSlug = (slug == null || slug.isBlank()) ? current.slug() : slugify(slug);
        String coverUrl = (coverImage != null && !coverImage.isEmpty())
                ? storeFile(coverImage, "covers")
                : current.coverUrl();
        return ResponseEntity.ok(ApiResponse.ok(
                seriesService.updateStory(seriesId, new SeriesUpsertRequest(resolvedSlug, resolvedTitle, resolvedDescription, coverUrl), categoryIds)
        ));
    }

    @DeleteMapping("/stories/{seriesId}")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteStory(@PathVariable Long seriesId) {
        seriesService.deleteStory(seriesId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true)));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> categories() {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.getAll()));
    }

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<CategoryDto>> createCategory(@Valid @RequestBody CategoryUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(categoryService.create(request)));
    }

    @PutMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<CategoryDto>> updateCategory(@PathVariable Long categoryId,
                                                                   @Valid @RequestBody CategoryUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.update(categoryId, request)));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteCategory(@PathVariable Long categoryId) {
        categoryService.delete(categoryId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true)));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AdminUserDto>>> users() {
        return ResponseEntity.ok(ApiResponse.ok(adminUserService.getUsers()));
    }

    @PutMapping("/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUserRole(@PathVariable Long userId,
                                                                            @RequestBody(required = false) Map<String, Object> payload) {
        String incomingRole = payload != null && payload.get("role") != null ? String.valueOf(payload.get("role")).trim() : "Viewer";
        final String requestedRole = incomingRole.isBlank() ? "Viewer" : incomingRole;

        Map<String, Object> currentUser = jdbcTemplate.query(
                """
                SELECT TOP 1 u.UserID, u.Email, u.Username, r.RoleName
                FROM Users u
                LEFT JOIN Roles r ON r.RoleID = u.RoleID
                WHERE u.UserID = ?
                """,
                rs -> {
                    if (!rs.next()) return null;
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("UserID", rs.getLong("UserID"));
                    row.put("Email", rs.getString("Email"));
                    row.put("Username", rs.getString("Username"));
                    row.put("RoleName", rs.getString("RoleName"));
                    return row;
                },
                userId
        );
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, Map.of("message", "User not found"), Map.of()));
        }

        Long roleId = jdbcTemplate.query(
                "SELECT TOP 1 RoleID FROM Roles WHERE LOWER(RoleName) = LOWER(?)",
                rs -> rs.next() ? rs.getLong(1) : null,
                requestedRole
        );
        if (roleId == null) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, Map.of("message", "Invalid role: " + requestedRole), Map.of()));
        }

        int updated = jdbcTemplate.update("UPDATE Users SET RoleID = ? WHERE UserID = ?", roleId, userId);
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, Map.of("message", "User not found"), Map.of()));
        }

        String oldRole = String.valueOf(currentUser.getOrDefault("RoleName", "Viewer"));
        String newRole = jdbcTemplate.query(
                "SELECT TOP 1 RoleName FROM Roles WHERE RoleID = ?",
                rs -> rs.next() ? rs.getString(1) : requestedRole,
                roleId
        );
        String normalizedOld = normalizeRoleName(oldRole);
        String normalizedNew = normalizeRoleName(newRole);
        String actor = Objects.requireNonNullElse(currentUserProvider.requireUser().email(), "Admin");
        String type = isUpgrade(normalizedOld, normalizedNew) ? "RoleUpgrade" : "RoleDowngrade";
        String title = isUpgrade(normalizedOld, normalizedNew)
                ? "TÃ i khoáº£n Ä‘Æ°á»£c nÃ¢ng cáº¥p quyá»n"
                : "TÃ i khoáº£n bá»‹ thay Ä‘á»•i quyá»n";
        String content = "Quyá»n cá»§a báº¡n Ä‘Ã£ Ä‘Æ°á»£c Ä‘á»•i tá»« " + normalizedOld + " sang " + normalizedNew + " bá»Ÿi " + actor + ".";

        notifyUser(userId, type, title, content, "/settings");

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "success", true,
                "userId", userId,
                "oldRole", normalizedOld,
                "role", normalizedNew
        )));
    }

    @PutMapping("/users/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateUserStatus(@PathVariable Long userId,
                                                                              @RequestBody(required = false) Map<String, Object> payload) {
        boolean isActive = payload != null && payload.get("isActive") != null && Boolean.parseBoolean(String.valueOf(payload.get("isActive")));
        int updated = jdbcTemplate.update("UPDATE Users SET IsActive = ? WHERE UserID = ?", isActive, userId);
        if (updated == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, Map.of("message", "User not found"), Map.of()));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("success", true, "userId", userId, "isActive", isActive)));
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> statistics() {
        Long totalMovies = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM Movies", Long.class);
        Long totalSeries = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM Series", Long.class);
        Long totalUsers = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM Users", Long.class);
        Long totalComments = jdbcTemplate.queryForObject(
                "SELECT COALESCE((SELECT COUNT(1) FROM MovieComments),0) + COALESCE((SELECT COUNT(1) FROM SeriesComments),0)",
                Long.class
        );
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "movies", totalMovies == null ? 0L : totalMovies,
                "series", totalSeries == null ? 0L : totalSeries,
                "users", totalUsers == null ? 0L : totalUsers,
                "comments", totalComments == null ? 0L : totalComments
        )));
    }

    @GetMapping("/stats/movies/views")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> movieViews(@RequestParam(defaultValue = "0") int days,
                                                                              @RequestParam(defaultValue = "50") int limit,
                                                                              @RequestParam(defaultValue = "0") int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int safeOffset = Math.max(0, offset);
        int safeDays = Math.max(0, days);

        String sql = """
                SELECT m.MovieID,
                       m.Title,
                       m.Slug,
                       m.PosterURL,
                       COALESCE(m.ViewCount, 0) AS totalViews,
                       COALESCE(v.uniqueViewers, 0) AS uniqueViewers,
                       v.lastWatchedAt
                FROM Movies m
                LEFT JOIN (
                    SELECT h.MovieID,
                           COUNT(DISTINCT h.UserID) AS uniqueViewers,
                           MAX(h.WatchedAt) AS lastWatchedAt
                    FROM MovieHistory h
                    WHERE (? = 0 OR h.WatchedAt >= DATEADD(DAY, -?, GETDATE()))
                    GROUP BY h.MovieID
                ) v ON v.MovieID = m.MovieID
                ORDER BY COALESCE(m.ViewCount, 0) DESC, m.MovieID DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;
        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("MovieID", rs.getLong("MovieID"));
            row.put("Title", rs.getString("Title"));
            row.put("Slug", rs.getString("Slug"));
            row.put("PosterURL", rs.getString("PosterURL"));
            row.put("totalViews", rs.getLong("totalViews"));
            row.put("uniqueViewers", rs.getLong("uniqueViewers"));
            row.put("lastWatchedAt", rs.getObject("lastWatchedAt"));
            return row;
        }, safeDays, safeDays, safeOffset, safeLimit);
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @GetMapping("/stats/movies/engagement")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> movieEngagement(@RequestParam(defaultValue = "100") int limit,
                                                                                   @RequestParam(defaultValue = "0") int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 300));
        int safeOffset = Math.max(0, offset);
        final int minVotes = 5;
        String sql = """
                WITH global_rating AS (
                    SELECT COALESCE(AVG(CAST(Rating AS FLOAT)), 0) AS globalAvg
                    FROM MovieRatings
                    WHERE Rating BETWEEN 1 AND 5
                )
                SELECT m.MovieID,
                       m.Title,
                       m.Slug,
                       m.PosterURL,
                       COALESCE(m.ViewCount, 0) AS totalViews,
                       COALESCE(f.totalLikes, 0) AS totalLikes,
                       COALESCE(c.totalComments, 0) AS totalComments,
                       COALESCE(r.totalRatings, 0) AS totalRatings,
                       COALESCE(r.avgRating, 0) AS rawAverageRating,
                       CASE
                           WHEN COALESCE(r.totalRatings, 0) = 0 THEN 0
                           ELSE (
                               (CAST(r.totalRatings AS FLOAT) / (CAST(r.totalRatings AS FLOAT) + ?)) * COALESCE(r.avgRating, 0)
                               + (? / (CAST(r.totalRatings AS FLOAT) + ?)) * g.globalAvg
                           )
                       END AS averageRating
                FROM Movies m
                CROSS JOIN global_rating g
                LEFT JOIN (
                    SELECT MovieID, COUNT(1) AS totalLikes
                    FROM MovieFavorites
                    GROUP BY MovieID
                ) f ON f.MovieID = m.MovieID
                LEFT JOIN (
                    SELECT MovieID, COUNT(1) AS totalComments
                    FROM MovieComments
                    WHERE COALESCE(IsDeleted, 0) = 0
                    GROUP BY MovieID
                ) c ON c.MovieID = m.MovieID
                LEFT JOIN (
                    SELECT MovieID, COUNT(1) AS totalRatings, AVG(CAST(Rating AS FLOAT)) AS avgRating
                    FROM MovieRatings
                    WHERE Rating BETWEEN 1 AND 5
                    GROUP BY MovieID
                ) r ON r.MovieID = m.MovieID
                ORDER BY COALESCE(m.ViewCount, 0) DESC, totalLikes DESC, totalComments DESC, m.MovieID DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;

        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("MovieID", rs.getLong("MovieID"));
            row.put("Title", rs.getString("Title"));
            row.put("Slug", rs.getString("Slug"));
            row.put("PosterURL", rs.getString("PosterURL"));
            row.put("totalViews", rs.getLong("totalViews"));
            row.put("totalLikes", rs.getLong("totalLikes"));
            row.put("totalComments", rs.getLong("totalComments"));
            row.put("totalRatings", rs.getLong("totalRatings"));
            row.put("rawAverageRating", rs.getDouble("rawAverageRating"));
            row.put("averageRating", rs.getDouble("averageRating"));
            return row;
        }, minVotes, (double) minVotes, (double) minVotes, safeOffset, safeLimit);
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @GetMapping("/stats/movies/{movieId}/details")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> movieMetricDetails(@PathVariable Long movieId,
                                                                                @RequestParam(defaultValue = "views") String metric,
                                                                                @RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String normalized = metric == null ? "views" : metric.trim().toLowerCase();
        String title = jdbcTemplate.query(
                "SELECT TOP 1 Title FROM Movies WHERE MovieID = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                movieId
        );
        if (title == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, Map.of("message", "Movie not found"), Map.of()));
        }

        List<Map<String, Object>> details;
        switch (normalized) {
            case "likes" -> details = jdbcTemplate.query(
                    """
                    SELECT TOP (?) f.FavoriteID,
                           f.CreatedAt,
                           u.UserID,
                           u.Email,
                           u.Username
                    FROM MovieFavorites f
                    LEFT JOIN Users u ON u.UserID = f.UserID
                    WHERE f.MovieID = ?
                    ORDER BY f.CreatedAt DESC, f.FavoriteID DESC
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new java.util.LinkedHashMap<>();
                        row.put("FavoriteID", rs.getLong("FavoriteID"));
                        row.put("CreatedAt", rs.getObject("CreatedAt"));
                        row.put("UserID", rs.getLong("UserID"));
                        row.put("Email", rs.getString("Email"));
                        row.put("Username", rs.getString("Username"));
                        return row;
                    },
                    safeLimit, movieId
            );
            case "comments" -> details = jdbcTemplate.query(
                    """
                    SELECT TOP (?) c.CommentID,
                           c.Content,
                           c.CreatedAt,
                           c.ParentCommentID,
                           u.UserID,
                           u.Email,
                           u.Username
                    FROM MovieComments c
                    LEFT JOIN Users u ON u.UserID = c.UserID
                    WHERE c.MovieID = ? AND COALESCE(c.IsDeleted, 0) = 0
                    ORDER BY c.CreatedAt DESC, c.CommentID DESC
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new java.util.LinkedHashMap<>();
                        row.put("CommentID", rs.getLong("CommentID"));
                        row.put("Content", rs.getString("Content"));
                        row.put("CreatedAt", rs.getObject("CreatedAt"));
                        row.put("ParentCommentID", rs.getObject("ParentCommentID"));
                        row.put("UserID", rs.getLong("UserID"));
                        row.put("Email", rs.getString("Email"));
                        row.put("Username", rs.getString("Username"));
                        return row;
                    },
                    safeLimit, movieId
            );
            case "ratings" -> details = jdbcTemplate.query(
                    """
                    SELECT TOP (?) r.RatingID,
                           r.Rating,
                           r.CreatedAt,
                           u.UserID,
                           u.Email,
                           u.Username
                    FROM MovieRatings r
                    LEFT JOIN Users u ON u.UserID = r.UserID
                    WHERE r.MovieID = ? AND r.Rating BETWEEN 1 AND 5
                    ORDER BY r.CreatedAt DESC, r.RatingID DESC
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new java.util.LinkedHashMap<>();
                        row.put("RatingID", rs.getLong("RatingID"));
                        row.put("Rating", rs.getObject("Rating"));
                        row.put("CreatedAt", rs.getObject("CreatedAt"));
                        row.put("UserID", rs.getLong("UserID"));
                        row.put("Email", rs.getString("Email"));
                        row.put("Username", rs.getString("Username"));
                        return row;
                    },
                    safeLimit, movieId
            );
            default -> {
                normalized = "views";
                details = jdbcTemplate.query(
                        """
                        SELECT TOP (?) h.HistoryID,
                               h.WatchedAt,
                               h.EpisodeID,
                               u.UserID,
                               u.Email,
                               u.Username,
                               e.EpisodeNumber,
                               e.Title AS EpisodeTitle
                        FROM MovieHistory h
                        LEFT JOIN Users u ON u.UserID = h.UserID
                        LEFT JOIN MovieEpisodes e ON e.EpisodeID = h.EpisodeID
                        WHERE h.MovieID = ?
                        ORDER BY h.WatchedAt DESC, h.HistoryID DESC
                        """,
                        (rs, rowNum) -> {
                            Map<String, Object> row = new java.util.LinkedHashMap<>();
                            row.put("HistoryID", rs.getLong("HistoryID"));
                            row.put("WatchedAt", rs.getObject("WatchedAt"));
                            row.put("EpisodeID", rs.getObject("EpisodeID"));
                            row.put("EpisodeNumber", rs.getObject("EpisodeNumber"));
                            row.put("EpisodeTitle", rs.getString("EpisodeTitle"));
                            row.put("UserID", rs.getLong("UserID"));
                            row.put("Email", rs.getString("Email"));
                            row.put("Username", rs.getString("Username"));
                            return row;
                        },
                        safeLimit, movieId
                );
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "movieId", movieId,
                "title", title,
                "metric", normalized,
                "items", details
        )));
    }

    @GetMapping("/stats/series/engagement")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> seriesEngagement(@RequestParam(defaultValue = "100") int limit,
                                                                                    @RequestParam(defaultValue = "0") int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 300));
        int safeOffset = Math.max(0, offset);
        final int minVotes = 5;
        String sql = """
                WITH global_rating AS (
                    SELECT COALESCE(AVG(CAST(Rating AS FLOAT)), 0) AS globalAvg
                    FROM SeriesRatings
                    WHERE Rating BETWEEN 1 AND 5
                )
                SELECT s.SeriesID,
                       s.Title,
                       s.Slug,
                       s.CoverURL,
                       COALESCE(s.ViewCount, 0) AS totalViews,
                       COALESCE(f.totalLikes, 0) AS totalLikes,
                       COALESCE(c.totalComments, 0) AS totalComments,
                       COALESCE(r.totalRatings, 0) AS totalRatings,
                       COALESCE(r.avgRating, 0) AS rawAverageRating,
                       CASE
                           WHEN COALESCE(r.totalRatings, 0) = 0 THEN 0
                           ELSE (
                               (CAST(r.totalRatings AS FLOAT) / (CAST(r.totalRatings AS FLOAT) + ?)) * COALESCE(r.avgRating, 0)
                               + (? / (CAST(r.totalRatings AS FLOAT) + ?)) * g.globalAvg
                           )
                       END AS averageRating
                FROM Series s
                CROSS JOIN global_rating g
                LEFT JOIN (
                    SELECT SeriesID, COUNT(1) AS totalLikes
                    FROM SeriesFavorites
                    GROUP BY SeriesID
                ) f ON f.SeriesID = s.SeriesID
                LEFT JOIN (
                    SELECT SeriesID, COUNT(1) AS totalComments
                    FROM SeriesComments
                    WHERE COALESCE(IsDeleted, 0) = 0
                    GROUP BY SeriesID
                ) c ON c.SeriesID = s.SeriesID
                LEFT JOIN (
                    SELECT SeriesID, COUNT(1) AS totalRatings, AVG(CAST(Rating AS FLOAT)) AS avgRating
                    FROM SeriesRatings
                    WHERE Rating BETWEEN 1 AND 5
                    GROUP BY SeriesID
                ) r ON r.SeriesID = s.SeriesID
                ORDER BY COALESCE(s.ViewCount, 0) DESC, totalLikes DESC, totalComments DESC, s.SeriesID DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;

        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("SeriesID", rs.getLong("SeriesID"));
            row.put("Title", rs.getString("Title"));
            row.put("Slug", rs.getString("Slug"));
            row.put("CoverURL", rs.getString("CoverURL"));
            row.put("totalViews", rs.getLong("totalViews"));
            row.put("totalLikes", rs.getLong("totalLikes"));
            row.put("totalComments", rs.getLong("totalComments"));
            row.put("totalRatings", rs.getLong("totalRatings"));
            row.put("rawAverageRating", rs.getDouble("rawAverageRating"));
            row.put("averageRating", rs.getDouble("averageRating"));
            return row;
        }, minVotes, (double) minVotes, (double) minVotes, safeOffset, safeLimit);
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @GetMapping("/stats/series/{seriesId}/details")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> seriesMetricDetails(@PathVariable Long seriesId,
                                                                                 @RequestParam(defaultValue = "views") String metric,
                                                                                 @RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String normalized = metric == null ? "views" : metric.trim().toLowerCase();
        String title = jdbcTemplate.query(
                "SELECT TOP 1 Title FROM Series WHERE SeriesID = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                seriesId
        );
        if (title == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, Map.of("message", "Series not found"), Map.of()));
        }

        List<Map<String, Object>> details;
        switch (normalized) {
            case "likes" -> details = jdbcTemplate.query(
                    """
                    SELECT TOP (?) f.FavoriteID,
                           f.CreatedAt,
                           u.UserID,
                           u.Email,
                           u.Username
                    FROM SeriesFavorites f
                    LEFT JOIN Users u ON u.UserID = f.UserID
                    WHERE f.SeriesID = ?
                    ORDER BY f.CreatedAt DESC, f.FavoriteID DESC
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new java.util.LinkedHashMap<>();
                        row.put("FavoriteID", rs.getLong("FavoriteID"));
                        row.put("CreatedAt", rs.getObject("CreatedAt"));
                        row.put("UserID", rs.getLong("UserID"));
                        row.put("Email", rs.getString("Email"));
                        row.put("Username", rs.getString("Username"));
                        return row;
                    },
                    safeLimit, seriesId
            );
            case "comments" -> details = jdbcTemplate.query(
                    """
                    SELECT TOP (?) c.CommentID,
                           c.Content,
                           c.CreatedAt,
                           c.ParentCommentID,
                           u.UserID,
                           u.Email,
                           u.Username
                    FROM SeriesComments c
                    LEFT JOIN Users u ON u.UserID = c.UserID
                    WHERE c.SeriesID = ? AND COALESCE(c.IsDeleted, 0) = 0
                    ORDER BY c.CreatedAt DESC, c.CommentID DESC
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new java.util.LinkedHashMap<>();
                        row.put("CommentID", rs.getLong("CommentID"));
                        row.put("Content", rs.getString("Content"));
                        row.put("CreatedAt", rs.getObject("CreatedAt"));
                        row.put("ParentCommentID", rs.getObject("ParentCommentID"));
                        row.put("UserID", rs.getLong("UserID"));
                        row.put("Email", rs.getString("Email"));
                        row.put("Username", rs.getString("Username"));
                        return row;
                    },
                    safeLimit, seriesId
            );
            case "ratings" -> details = jdbcTemplate.query(
                    """
                    SELECT TOP (?) r.RatingID,
                           r.Rating,
                           r.CreatedAt,
                           u.UserID,
                           u.Email,
                           u.Username
                    FROM SeriesRatings r
                    LEFT JOIN Users u ON u.UserID = r.UserID
                    WHERE r.SeriesID = ? AND r.Rating BETWEEN 1 AND 5
                    ORDER BY r.CreatedAt DESC, r.RatingID DESC
                    """,
                    (rs, rowNum) -> {
                        Map<String, Object> row = new java.util.LinkedHashMap<>();
                        row.put("RatingID", rs.getLong("RatingID"));
                        row.put("Rating", rs.getObject("Rating"));
                        row.put("CreatedAt", rs.getObject("CreatedAt"));
                        row.put("UserID", rs.getLong("UserID"));
                        row.put("Email", rs.getString("Email"));
                        row.put("Username", rs.getString("Username"));
                        return row;
                    },
                    safeLimit, seriesId
            );
            default -> {
                normalized = "views";
                details = jdbcTemplate.query(
                        """
                        SELECT TOP (?) h.HistoryID,
                               h.ReadAt,
                               h.ChapterID,
                               u.UserID,
                               u.Email,
                               u.Username,
                               c.ChapterNumber,
                               c.Title AS ChapterTitle
                        FROM SeriesHistory h
                        LEFT JOIN Users u ON u.UserID = h.UserID
                        LEFT JOIN Chapters c ON c.ChapterID = h.ChapterID
                        WHERE h.SeriesID = ?
                        ORDER BY h.ReadAt DESC, h.HistoryID DESC
                        """,
                        (rs, rowNum) -> {
                            Map<String, Object> row = new java.util.LinkedHashMap<>();
                            row.put("HistoryID", rs.getLong("HistoryID"));
                            row.put("ReadAt", rs.getObject("ReadAt"));
                            row.put("ChapterID", rs.getObject("ChapterID"));
                            row.put("ChapterNumber", rs.getObject("ChapterNumber"));
                            row.put("ChapterTitle", rs.getString("ChapterTitle"));
                            row.put("UserID", rs.getLong("UserID"));
                            row.put("Email", rs.getString("Email"));
                            row.put("Username", rs.getString("Username"));
                            return row;
                        },
                        safeLimit, seriesId
                );
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "seriesId", seriesId,
                "title", title,
                "metric", normalized,
                "items", details
        )));
    }

    @GetMapping("/comments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> commentsModeration() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "TODO: implement comments moderation list")));
    }

    @PostMapping("/comments/{commentId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approveComment(@PathVariable Long commentId) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("commentId", commentId, "approved", true)));
    }

    @DeleteMapping("/comments/{commentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteComment(@PathVariable Long commentId) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("commentId", commentId, "deleted", true)));
    }

    @GetMapping("/role-upgrade-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> roleUpgradeRequests(@RequestParam(required = false) String status) {
        String normalizedStatus = status == null ? null : status.trim();
        List<Map<String, Object>> rows = jdbcTemplate.query(
                """
                SELECT r.RequestID,
                       r.UserID,
                       u.Username,
                       u.Email,
                       cur.RoleName AS CurrentRole,
                       req.RoleName AS RequestedRole,
                       r.Reason,
                       r.Status,
                       r.ReviewNote,
                       r.RequestedAt,
                       r.ReviewedAt
                FROM RoleUpgradeRequests r
                LEFT JOIN Users u ON u.UserID = r.UserID
                LEFT JOIN Roles cur ON cur.RoleID = u.RoleID
                LEFT JOIN Roles req ON req.RoleID = r.RequestedRoleID
                WHERE (? IS NULL OR r.Status = ?)
                ORDER BY CASE WHEN r.Status = 'Pending' THEN 0 ELSE 1 END, r.RequestedAt DESC, r.RequestID DESC
                """,
                (rs, rowNum) -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("RequestID", rs.getLong("RequestID"));
                    row.put("UserID", rs.getLong("UserID"));
                    row.put("Username", rs.getString("Username"));
                    row.put("Email", rs.getString("Email"));
                    row.put("CurrentRole", normalizeRoleName(rs.getString("CurrentRole")));
                    row.put("RequestedRole", normalizeRoleName(rs.getString("RequestedRole")));
                    row.put("Reason", rs.getString("Reason"));
                    row.put("Status", rs.getString("Status"));
                    row.put("ReviewNote", rs.getString("ReviewNote"));
                    row.put("RequestedAt", rs.getObject("RequestedAt"));
                    row.put("ReviewedAt", rs.getObject("ReviewedAt"));
                    return row;
                },
                normalizedStatus,
                normalizedStatus
        );
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    @PostMapping("/role-upgrade-requests/{requestId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approveRoleUpgrade(@PathVariable Long requestId) {
        return handleRoleUpgradeRequest(requestId, Map.of("action", "approve"));
    }

    @PostMapping("/role-upgrade-requests/{requestId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> rejectRoleUpgrade(@PathVariable Long requestId) {
        return handleRoleUpgradeRequest(requestId, Map.of("action", "reject"));
    }

    @PutMapping("/role-upgrade-requests/{requestId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleRoleUpgradeRequest(@PathVariable Long requestId,
                                                                                      @RequestBody(required = false) Map<String, Object> payload) {
        String action = payload != null && payload.get("action") != null ? String.valueOf(payload.get("action")).trim() : "approve";
        boolean approve = "approve".equalsIgnoreCase(action);
        boolean reject = "reject".equalsIgnoreCase(action);
        if (!approve && !reject) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, Map.of("message", "action must be approve or reject"), Map.of()));
        }

        Map<String, Object> requestRow = jdbcTemplate.query(
                """
                SELECT TOP 1 r.RequestID,
                             r.UserID,
                             r.RequestedRoleID,
                             r.Status,
                             u.RoleID AS CurrentRoleID,
                             u.Email,
                             req.RoleName AS RequestedRoleName,
                             cur.RoleName AS CurrentRoleName
                FROM RoleUpgradeRequests r
                LEFT JOIN Users u ON u.UserID = r.UserID
                LEFT JOIN Roles req ON req.RoleID = r.RequestedRoleID
                LEFT JOIN Roles cur ON cur.RoleID = u.RoleID
                WHERE r.RequestID = ?
                """,
                rs -> {
                    if (!rs.next()) return null;
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("RequestID", rs.getLong("RequestID"));
                    row.put("UserID", rs.getLong("UserID"));
                    row.put("RequestedRoleID", rs.getLong("RequestedRoleID"));
                    row.put("Status", rs.getString("Status"));
                    row.put("CurrentRoleID", rs.getLong("CurrentRoleID"));
                    row.put("Email", rs.getString("Email"));
                    row.put("RequestedRoleName", rs.getString("RequestedRoleName"));
                    row.put("CurrentRoleName", rs.getString("CurrentRoleName"));
                    return row;
                },
                requestId
        );
        if (requestRow == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, Map.of("message", "Request not found"), Map.of()));
        }
        String currentStatus = String.valueOf(requestRow.getOrDefault("Status", "Pending"));
        boolean force = payload != null && Boolean.parseBoolean(String.valueOf(payload.getOrDefault("force", "false")));
        if (!force && !"Pending".equalsIgnoreCase(currentStatus)) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, Map.of("message", "Request was already handled"), Map.of()));
        }

        Long userId = ((Number) requestRow.get("UserID")).longValue();
        Long requestedRoleId = ((Number) requestRow.get("RequestedRoleID")).longValue();
        String requestedRoleName = normalizeRoleName(String.valueOf(requestRow.getOrDefault("RequestedRoleName", "Viewer")));
        String reviewNote = payload != null && payload.get("note") != null ? String.valueOf(payload.get("note")) : null;
        String actor = Objects.requireNonNullElse(currentUserProvider.requireUser().email(), "admin");

        if (approve) {
            jdbcTemplate.update("UPDATE Users SET RoleID = ? WHERE UserID = ?", requestedRoleId, userId);
        }

        String nextStatus = approve ? "Approved" : "Rejected";
        jdbcTemplate.update(
                """
                UPDATE RoleUpgradeRequests
                SET Status = ?, ReviewNote = ?, ReviewedBy = ?, ReviewedAt = GETDATE()
                WHERE RequestID = ?
                """,
                nextStatus,
                reviewNote,
                currentUserProvider.requireUser().userId(),
                requestId
        );

        if (approve) {
            notifyUser(
                    userId,
                    "RoleUpgradeApproved",
                    "Role upgrade approved",
                    "Your role-upgrade request was approved. New role: " + requestedRoleName + ".",
                    "/settings"
            );
        } else {
            notifyUser(
                    userId,
                    "RoleUpgradeRejected",
                    "Role upgrade rejected",
                    "Your role-upgrade request was rejected by " + actor + ".",
                    "/settings"
            );
        }

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "requestId", requestId,
                "status", nextStatus,
                "approved", approve
        )));
    }

    @PostMapping("/movies/{movieId}/episodes")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadEpisode(@PathVariable Long movieId,
                                                                          @RequestParam(required = false) String title,
                                                                          @RequestPart(required = false) MultipartFile videoFile) {
        String videoUrl = (videoFile != null && !videoFile.isEmpty()) ? storeFile(videoFile, "videos") : null;
        var episode = movieService.addEpisode(movieId, title, videoUrl);
        if (isMovieApproved(movieId)) {
            notifyMovieFavoritesNewEpisode(movieId, title);
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("movieId", movieId, "episode", episode)));
    }

    @GetMapping("/movies/{movieId}/episodes")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<List<?>>> listEpisodes(@PathVariable Long movieId) {
        return ResponseEntity.ok(ApiResponse.ok(movieService.getEpisodesByMovieId(movieId)));
    }

    @PutMapping("/episodes/{episodeId}")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateEpisode(@PathVariable Long episodeId,
                                                                          @RequestParam(required = false) String title,
                                                                          @RequestPart(required = false) MultipartFile videoFile) {
        String videoUrl = (videoFile != null && !videoFile.isEmpty()) ? storeFile(videoFile, "videos") : null;
        var episode = movieService.updateEpisode(episodeId, title, videoUrl);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("episode", episode)));
    }

    @DeleteMapping("/episodes/{episodeId}")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteEpisode(@PathVariable Long episodeId) {
        movieService.deleteEpisode(episodeId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true, "episodeId", episodeId)));
    }

    @PutMapping("/movies/{movieId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateMovieStatus(@PathVariable Long movieId,
                                                                               @RequestBody(required = false) Map<String, Object> payload) {
        String status = payload != null && payload.get("status") != null ? String.valueOf(payload.get("status")) : "Pending";
        movieService.updateMovieStatus(movieId, status);
        String normalizedStatus = status.trim();
        Long uploaderId = jdbcTemplate.query(
                "SELECT TOP 1 UploaderID FROM Movies WHERE MovieID = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                movieId
        );
        String title = jdbcTemplate.query(
                "SELECT TOP 1 Title FROM Movies WHERE MovieID = ?",
                rs -> rs.next() ? rs.getString(1) : "phim",
                movieId
        );
        String slug = jdbcTemplate.query(
                "SELECT TOP 1 Slug FROM Movies WHERE MovieID = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                movieId
        );
        String actor = Objects.requireNonNullElse(currentUserProvider.requireUser().email(), "Admin");
        String relatedUrl = slug == null || slug.isBlank() ? "/movies" : "/watch/" + slug;

        if ("Approved".equalsIgnoreCase(normalizedStatus)) {
            if (uploaderId != null) {
                notifyUser(
                        uploaderId,
                        "UploadApproved",
                        "Phim cá»§a báº¡n Ä‘Ã£ Ä‘Æ°á»£c duyá»‡t",
                        "Phim \"" + title + "\" Ä‘Ã£ Ä‘Æ°á»£c " + actor + " duyá»‡t.",
                        relatedUrl
                );
            }
        } else if ("Rejected".equalsIgnoreCase(normalizedStatus)) {
            if (uploaderId != null) {
                notifyUser(
                        uploaderId,
                        "UploadRejected",
                        "Phim cá»§a báº¡n bá»‹ tá»« chá»‘i",
                        "Phim \"" + title + "\" Ä‘Ã£ bá»‹ " + actor + " tá»« chá»‘i.",
                        "/user/upload"
                );
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("movieId", movieId, "status", status)));
    }

    @PutMapping("/stories/{seriesId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateStoryStatus(@PathVariable Long seriesId,
                                                                               @RequestBody(required = false) Map<String, Object> payload) {
        String status = payload != null && payload.get("status") != null ? String.valueOf(payload.get("status")) : "Pending";
        String normalizedStatus = status.trim();

        Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM Series WHERE SeriesID = ?", Integer.class, seriesId);
        if (exists == null || exists == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, Map.of("error", "Story not found"), Map.of()));
        }

        jdbcTemplate.update("UPDATE Series SET Status = ? WHERE SeriesID = ?", normalizedStatus, seriesId);
        if (columnExists("Series", "IsApproved")) {
            boolean approved = "approved".equalsIgnoreCase(normalizedStatus);
            jdbcTemplate.update("UPDATE Series SET IsApproved = ? WHERE SeriesID = ?", approved, seriesId);
        }
        if (columnExists("Series", "ApprovedAt") && "approved".equalsIgnoreCase(normalizedStatus)) {
            jdbcTemplate.update("UPDATE Series SET ApprovedAt = COALESCE(ApprovedAt, GETDATE()) WHERE SeriesID = ?", seriesId);
        }

        Long uploaderId = jdbcTemplate.query(
                "SELECT TOP 1 UploaderID FROM Series WHERE SeriesID = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                seriesId
        );
        String title = jdbcTemplate.query(
                "SELECT TOP 1 Title FROM Series WHERE SeriesID = ?",
                rs -> rs.next() ? rs.getString(1) : "truyá»‡n",
                seriesId
        );
        String slug = jdbcTemplate.query(
                "SELECT TOP 1 Slug FROM Series WHERE SeriesID = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                seriesId
        );
        String actor = Objects.requireNonNullElse(currentUserProvider.requireUser().email(), "Admin");
        String relatedUrl = slug == null || slug.isBlank() ? "/stories" : "/stories/" + slug;

        if ("Approved".equalsIgnoreCase(normalizedStatus)) {
            if (uploaderId != null) {
                notifyUser(
                        uploaderId,
                        "UploadApproved",
                        "Truyá»‡n cá»§a báº¡n Ä‘Ã£ Ä‘Æ°á»£c duyá»‡t",
                        "Truyá»‡n \"" + title + "\" Ä‘Ã£ Ä‘Æ°á»£c " + actor + " duyá»‡t.",
                        relatedUrl
                );
            }
        } else if ("Rejected".equalsIgnoreCase(normalizedStatus)) {
            if (uploaderId != null) {
                notifyUser(
                        uploaderId,
                        "UploadRejected",
                        "Truyá»‡n cá»§a báº¡n bá»‹ tá»« chá»‘i",
                        "Truyá»‡n \"" + title + "\" Ä‘Ã£ bá»‹ " + actor + " tá»« chá»‘i.",
                        "/upload/stories"
                );
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("seriesId", seriesId, "status", normalizedStatus)));
    }

    private int createInitialStoryChapters(Long seriesId,
                                           String storyType,
                                           List<MultipartFile> contentFiles,
                                           List<MultipartFile> chapterImages,
                                           String crawledImagesJson,
                                           String chaptersJson) {
        String normalizedStoryType = storyType == null ? "Text" : storyType.trim();
        List<String> chapterTitles = parseChapterTitles(chaptersJson);
        int created = 0;

        if ("comic".equalsIgnoreCase(normalizedStoryType)) {
            List<String> crawledImagePaths = parseCrawledImagePaths(crawledImagesJson);
            if (!crawledImagePaths.isEmpty()) {
                Integer nextNo = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(MAX(ChapterNumber), 0) + 1 FROM Chapters WHERE SeriesID = ?",
                        Integer.class,
                        seriesId
                );
                int chapterNo = nextNo == null ? 1 : Math.max(1, nextNo);
                String chapterTitle = chapterTitles.isEmpty() ? ("Chapter " + chapterNo) : chapterTitles.getFirst();
                Long chapterId = insertStoryChapter(seriesId, chapterNo, chapterTitle, null, crawledImagePaths.size());
                if (chapterId != null) {
                    insertChapterImages(chapterId, crawledImagePaths);
                    created++;
                }
                return created;
            }

            if (chapterImages != null && !chapterImages.isEmpty()) {
                Integer nextNo = jdbcTemplate.queryForObject(
                        "SELECT COALESCE(MAX(ChapterNumber), 0) + 1 FROM Chapters WHERE SeriesID = ?",
                        Integer.class,
                        seriesId
                );
                int chapterNo = nextNo == null ? 1 : Math.max(1, nextNo);
                String chapterTitle = chapterTitles.isEmpty() ? ("Chapter " + chapterNo) : chapterTitles.getFirst();
                List<String> imagePaths = new ArrayList<>();
                for (MultipartFile image : chapterImages) {
                    if (image != null && !image.isEmpty()) {
                        imagePaths.add(storeFile(image, "chapters"));
                    }
                }
                if (!imagePaths.isEmpty()) {
                    Long chapterId = insertStoryChapter(seriesId, chapterNo, chapterTitle, null, imagePaths.size());
                    if (chapterId != null) {
                        insertChapterImages(chapterId, imagePaths);
                        created++;
                    }
                }
            }
            return created;
        }

        if (contentFiles != null && !contentFiles.isEmpty()) {
            Integer nextNo = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(ChapterNumber), 0) + 1 FROM Chapters WHERE SeriesID = ?",
                    Integer.class,
                    seriesId
            );
            int chapterNo = nextNo == null ? 1 : Math.max(1, nextNo);
            for (int i = 0; i < contentFiles.size(); i++) {
                MultipartFile file = contentFiles.get(i);
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String chapterTitle = i < chapterTitles.size() && chapterTitles.get(i) != null && !chapterTitles.get(i).isBlank()
                        ? chapterTitles.get(i)
                        : ("Chapter " + chapterNo);
                String contentPath = storeFile(file, "chapters");
                Long chapterId = insertStoryChapter(seriesId, chapterNo, chapterTitle, contentPath, 0);
                if (chapterId != null) {
                    created++;
                }
                chapterNo++;
            }
        }
        return created;
    }

    private Long insertStoryChapter(Long seriesId, int chapterNo, String title, String contentPath, int imageCount) {
        String chapterTitle = (title == null || title.isBlank()) ? ("Chapter " + chapterNo) : title.trim();
        boolean hasContent = columnExists("Chapters", "Content");
        boolean hasImageCount = columnExists("Chapters", "ImageCount");
        boolean hasChapterCode = columnExists("Chapters", "ChapterCode");
        String chapterCode = "CH" + seriesId + "-" + String.format("%03d", chapterNo);

        StringBuilder sql = new StringBuilder("INSERT INTO Chapters (SeriesID, ChapterNumber, Title");
        List<Object> params = new ArrayList<>();
        params.add(seriesId);
        params.add(chapterNo);
        params.add(chapterTitle);

        if (hasContent) {
            sql.append(", Content");
            params.add(contentPath);
        }
        if (hasImageCount) {
            sql.append(", ImageCount");
            params.add(imageCount);
        }
        if (hasChapterCode) {
            sql.append(", ChapterCode");
            params.add(chapterCode);
        }

        sql.append(") VALUES (?");
        for (int i = 1; i < params.size(); i++) {
            sql.append(", ?");
        }
        sql.append(")");
        jdbcTemplate.update(sql.toString(), params.toArray());

        return jdbcTemplate.query(
                """
                SELECT TOP 1 ChapterID
                FROM Chapters
                WHERE SeriesID = ? AND ChapterNumber = ?
                ORDER BY ChapterID DESC
                """,
                rs -> rs.next() ? rs.getLong(1) : null,
                seriesId, chapterNo
        );
    }

    private void insertChapterImages(Long chapterId, List<String> imagePaths) {
        if (chapterId == null || imagePaths == null || imagePaths.isEmpty() || !tableExists("ChapterImages")) {
            return;
        }
        boolean hasCreatedAt = columnExists("ChapterImages", "CreatedAt");
        int order = 1;
        for (String raw : imagePaths) {
            String imagePath = normalizeIncomingPath(raw);
            if (imagePath == null || imagePath.isBlank()) {
                continue;
            }
            if (hasCreatedAt) {
                jdbcTemplate.update(
                        "INSERT INTO ChapterImages (ChapterID, ImageURL, ImageOrder, CreatedAt) VALUES (?, ?, ?, GETDATE())",
                        chapterId, imagePath, order++
                );
            } else {
                jdbcTemplate.update(
                        "INSERT INTO ChapterImages (ChapterID, ImageURL, ImageOrder) VALUES (?, ?, ?)",
                        chapterId, imagePath, order++
                );
            }
        }
    }

    private List<String> parseChapterTitles(String chaptersJson) {
        if (chaptersJson == null || chaptersJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(chaptersJson);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> titles = new ArrayList<>();
            for (JsonNode item : node) {
                if (item != null && item.hasNonNull("title")) {
                    titles.add(item.get("title").asText().trim());
                }
            }
            return titles;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> parseCrawledImagePaths(String crawledImagesJson) {
        if (crawledImagesJson == null || crawledImagesJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(crawledImagesJson);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> items = new ArrayList<>();
            for (JsonNode item : node) {
                if (item != null && item.isTextual()) {
                    items.add(item.asText());
                }
            }
            return items;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @PostMapping(value = "/stories/{seriesId}/chapters", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadChapter(@PathVariable Long seriesId,
                                                                           @RequestParam(required = false) String title,
                                                                           @RequestParam(required = false) String storyType,
                                                                           @RequestPart(required = false) MultipartFile contentFile,
                                                                           @RequestPart(required = false) List<MultipartFile> chapterImages) {
        AuthenticatedUser current = currentUserProvider.requireUser();
        requireSeriesOwnerOrAdmin(seriesId, current);

        Integer nextChapterNumber = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(ChapterNumber), 0) + 1 FROM Chapters WHERE SeriesID = ?",
                Integer.class,
                seriesId
        );
        int chapterNumber = nextChapterNumber == null ? 1 : Math.max(1, nextChapterNumber);
        String chapterTitle = (title == null || title.isBlank()) ? ("Chapter " + chapterNumber) : title.trim();
        boolean isComic = storyType != null && "comic".equalsIgnoreCase(storyType.trim());
        String contentValue = null;
        if (!isComic && contentFile != null && !contentFile.isEmpty()) {
            contentValue = storeFile(contentFile, "chapters");
        }

        List<String> imagePaths = new ArrayList<>();
        if (chapterImages != null) {
            for (MultipartFile image : chapterImages) {
                if (image == null || image.isEmpty()) {
                    continue;
                }
                imagePaths.add(storeFile(image, "chapters"));
            }
        }

        if (isComic && imagePaths.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, Map.of("error", "Truyá»‡n tranh cáº§n Ã­t nháº¥t má»™t áº£nh chÆ°Æ¡ng"), Map.of())
            );
        }
        if (!isComic && (contentFile == null || contentFile.isEmpty())) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, Map.of("error", "Truyá»‡n chá»¯ cáº§n file ná»™i dung chÆ°Æ¡ng"), Map.of())
            );
        }

        Long chapterId = insertStoryChapter(seriesId, chapterNumber, chapterTitle, contentValue, imagePaths.size());

        int imageCount = imagePaths.size();
        insertChapterImages(chapterId, imagePaths);

        if (chapterId != null && columnExists("Chapters", "ImageCount")) {
            jdbcTemplate.update("UPDATE Chapters SET ImageCount = ? WHERE ChapterID = ?", imageCount, chapterId);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("seriesId", seriesId);
        payload.put("chapterId", chapterId);
        payload.put("chapterNumber", chapterNumber);
        payload.put("title", chapterTitle);
        payload.put("imageCount", imageCount);
        if (isSeriesApproved(seriesId)) {
            notifySeriesFavoritesNewChapter(seriesId, chapterTitle, chapterNumber);
        }
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    @PostMapping(value = "/stories/{seriesId}/chapters/crawl", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawlAndCreateChapter(@PathVariable Long seriesId,
                                                                                   @RequestBody(required = false) Map<String, Object> payload) {
        AuthenticatedUser current = currentUserProvider.requireUser();
        requireSeriesOwnerOrAdmin(seriesId, current);

        Map<String, Object> request = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        request.put("saveToDisk", true);

        Map<String, Object> crawlResult;
        try {
            crawlResult = crawlStoryService.crawlChapter(request);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, Map.of("error", ex.getMessage()), Map.of())
            );
        }
        Object savedImagesRaw = crawlResult.get("savedImages");
        List<String> savedImages = new ArrayList<>();
        if (savedImagesRaw instanceof List<?> list) {
            for (Object item : list) {
                String imagePath = normalizeIncomingPath(item == null ? null : String.valueOf(item));
                if (imagePath != null && !imagePath.isBlank()) {
                    savedImages.add(imagePath);
                }
            }
        }

        if (savedImages.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, Map.of("error", "Crawl áº£nh tháº¥t báº¡i hoáº·c khÃ´ng cÃ³ áº£nh há»£p lá»‡"), Map.of())
            );
        }

        Integer nextChapterNumber = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(ChapterNumber), 0) + 1 FROM Chapters WHERE SeriesID = ?",
                Integer.class,
                seriesId
        );
        int chapterNumber = nextChapterNumber == null ? 1 : Math.max(1, nextChapterNumber);

        String incomingTitle = request.get("chapterTitle") == null ? null : String.valueOf(request.get("chapterTitle"));
        String chapterTitle = (incomingTitle == null || incomingTitle.isBlank())
                ? "Chapter " + chapterNumber
                : incomingTitle.trim();

        Long chapterId = insertStoryChapter(seriesId, chapterNumber, chapterTitle, null, savedImages.size());
        insertChapterImages(chapterId, savedImages);

        if (chapterId != null && columnExists("Chapters", "ImageCount")) {
            jdbcTemplate.update("UPDATE Chapters SET ImageCount = ? WHERE ChapterID = ?", savedImages.size(), chapterId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("seriesId", seriesId);
        result.put("chapterId", chapterId);
        result.put("chapterNumber", chapterNumber);
        result.put("title", chapterTitle);
        result.put("imageCount", savedImages.size());
        result.put("savedImages", savedImages);
        result.put("storageFolder", crawlResult.get("storageFolder"));
        if (isSeriesApproved(seriesId)) {
            notifySeriesFavoritesNewChapter(seriesId, chapterTitle, chapterNumber);
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PutMapping(value = "/chapters/{chapterId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateChapter(@PathVariable Long chapterId,
                                                                           @RequestParam(required = false) String title,
                                                                           @RequestParam(required = false) String chapterCode) {
        AuthenticatedUser current = currentUserProvider.requireUser();
        requireChapterOwnerOrAdmin(chapterId, current);

        Map<String, Object> updates = new LinkedHashMap<>();
        if (title != null && !title.isBlank()) {
            updates.put("Title", title.trim());
        }
        if (chapterCode != null && columnExists("Chapters", "ChapterCode")) {
            updates.put("ChapterCode", chapterCode.trim());
        }
        if (updates.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(Map.of("updated", false, "chapterId", chapterId)));
        }

        String setClause = String.join(", ", updates.keySet().stream().map(k -> k + " = ?").toList());
        List<Object> params = new ArrayList<>(updates.values());
        params.add(chapterId);
        int affected = jdbcTemplate.update("UPDATE Chapters SET " + setClause + " WHERE ChapterID = ?", params.toArray());
        if (affected <= 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, Map.of("error", "Chapter not found"), Map.of()));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("updated", true, "chapterId", chapterId)));
    }

    @DeleteMapping("/chapters/{chapterId}")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteChapter(@PathVariable Long chapterId) {
        AuthenticatedUser current = currentUserProvider.requireUser();
        requireChapterOwnerOrAdmin(chapterId, current);

        jdbcTemplate.update("DELETE FROM SeriesHistory WHERE ChapterID = ?", chapterId);
        if (tableExists("ChapterImages")) {
            jdbcTemplate.update("DELETE FROM ChapterImages WHERE ChapterID = ?", chapterId);
        }
        int deleted = jdbcTemplate.update("DELETE FROM Chapters WHERE ChapterID = ?", chapterId);
        if (deleted <= 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse<>(false, Map.of("error", "Chapter not found"), Map.of()));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true, "chapterId", chapterId)));
    }

    private String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "movie-" + System.currentTimeMillis();
        }
        String slug = value.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-");
        if (slug.isBlank()) {
            return "movie-" + System.currentTimeMillis();
        }
        return slug;
    }

    private MovieDto createMovieWithEpisodes(MovieUpsertRequest request,
                                             Long uploaderId,
                                             List<Long> categoryIds,
                                             MultipartFile videoFile,
                                             List<MultipartFile> episodeFiles,
                                             List<EpisodeMeta> episodeMetas) {
        MovieDto created = movieService.createMovie(request, uploaderId, categoryIds);
        boolean hasEpisode = false;

        if (episodeFiles != null && !episodeFiles.isEmpty()) {
            for (int i = 0; i < episodeFiles.size(); i++) {
                MultipartFile file = episodeFiles.get(i);
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String episodeTitle = "Episode " + (i + 1);
                if (episodeMetas != null && i < episodeMetas.size() && episodeMetas.get(i).title() != null && !episodeMetas.get(i).title().isBlank()) {
                    episodeTitle = episodeMetas.get(i).title();
                }
                movieService.addEpisode(created.id(), episodeTitle, storeFile(file, "videos"));
                hasEpisode = true;
            }
        }

        if (!hasEpisode && videoFile != null && !videoFile.isEmpty()) {
            movieService.addEpisode(created.id(), "Episode 1", storeFile(videoFile, "videos"));
        }

        movieService.updateMovieStatus(created.id(), "Approved");
        return created;
    }

    private void markSeriesApproved(Long seriesId) {
        jdbcTemplate.update("UPDATE Series SET Status = 'Approved' WHERE SeriesID = ?", seriesId);
        if (columnExists("Series", "IsApproved")) {
            jdbcTemplate.update("UPDATE Series SET IsApproved = 1 WHERE SeriesID = ?", seriesId);
        }
        if (columnExists("Series", "ApprovedAt")) {
            jdbcTemplate.update("UPDATE Series SET ApprovedAt = COALESCE(ApprovedAt, GETDATE()) WHERE SeriesID = ?", seriesId);
        }
    }

    private boolean isMovieApproved(Long movieId) {
        String status = jdbcTemplate.query(
                "SELECT TOP 1 Status FROM Movies WHERE MovieID = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                movieId
        );
        return status != null && "approved".equalsIgnoreCase(status.trim());
    }

    private boolean isSeriesApproved(Long seriesId) {
        String status = jdbcTemplate.query(
                "SELECT TOP 1 Status FROM Series WHERE SeriesID = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                seriesId
        );
        return status != null && "approved".equalsIgnoreCase(status.trim());
    }

    private void notifyMovieFavoritesNewEpisode(Long movieId, String episodeTitle) {
        String slug = jdbcTemplate.query(
                "SELECT TOP 1 Slug FROM Movies WHERE MovieID = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                movieId
        );
        String movieTitle = jdbcTemplate.query(
                "SELECT TOP 1 Title FROM Movies WHERE MovieID = ?",
                rs -> rs.next() ? rs.getString(1) : "phim",
                movieId
        );
        String ep = (episodeTitle == null || episodeTitle.isBlank()) ? "táº­p má»›i" : episodeTitle.trim();
        String relatedUrl = slug == null || slug.isBlank() ? "/movies" : "/watch/" + slug;
        jdbcTemplate.update(
                """
                INSERT INTO Notifications (UserID, Type, Title, Content, RelatedURL, IsRead, CreatedAt)
                SELECT DISTINCT mf.UserID, ?, ?, ?, ?, 0, GETDATE()
                FROM MovieFavorites mf
                INNER JOIN Users u ON u.UserID = mf.UserID
                WHERE mf.MovieID = ? AND u.IsActive = 1
                """,
                "FavoriteUpdate",
                "Phim báº¡n yÃªu thÃ­ch cÃ³ táº­p má»›i",
                "Phim \"" + movieTitle + "\" vá»«a ra " + ep + ".",
                relatedUrl,
                movieId
        );
    }

    private void notifySeriesFavoritesNewChapter(Long seriesId, String chapterTitle, int chapterNumber) {
        String slug = jdbcTemplate.query(
                "SELECT TOP 1 Slug FROM Series WHERE SeriesID = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                seriesId
        );
        String storyTitle = jdbcTemplate.query(
                "SELECT TOP 1 Title FROM Series WHERE SeriesID = ?",
                rs -> rs.next() ? rs.getString(1) : "truyá»‡n",
                seriesId
        );
        String chap = (chapterTitle == null || chapterTitle.isBlank()) ? ("Chapter " + chapterNumber) : chapterTitle.trim();
        String relatedUrl = slug == null || slug.isBlank()
                ? "/stories"
                : "/stories/" + slug + "?chapter=" + chapterNumber;
        jdbcTemplate.update(
                """
                INSERT INTO Notifications (UserID, Type, Title, Content, RelatedURL, IsRead, CreatedAt)
                SELECT DISTINCT sf.UserID, ?, ?, ?, ?, 0, GETDATE()
                FROM SeriesFavorites sf
                INNER JOIN Users u ON u.UserID = sf.UserID
                WHERE sf.SeriesID = ? AND u.IsActive = 1
                """,
                "FavoriteUpdate",
                "Truyá»‡n báº¡n yÃªu thÃ­ch cÃ³ chÆ°Æ¡ng má»›i",
                "Truyá»‡n \"" + storyTitle + "\" vá»«a ra " + chap + ".",
                relatedUrl,
                seriesId
        );
    }

    private void notifyUser(Long userId, String type, String title, String content, String relatedUrl) {
        if (userId == null) {
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO Notifications (UserID, Type, Title, Content, RelatedURL, IsRead, CreatedAt)
                VALUES (?, ?, ?, ?, ?, 0, GETDATE())
                """,
                userId, type, title, content, relatedUrl
        );
    }

    private String normalizeRoleName(String raw) {
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

    private boolean isUpgrade(String oldRole, String newRole) {
        return roleRank(newRole) > roleRank(oldRole);
    }

    private int roleRank(String role) {
        return switch (normalizeRoleName(role)) {
            case "Viewer" -> 0;
            case "Reup", "Translator", "Author", "Uploader" -> 1;
            case "Admin" -> 2;
            default -> 0;
        };
    }

    private List<EpisodeMeta> parseEpisodeMeta(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (!node.isArray()) {
                return List.of();
            }
            List<EpisodeMeta> result = new ArrayList<>();
            for (JsonNode item : node) {
                String title = item.hasNonNull("title") ? item.get("title").asText() : null;
                Integer episodeNumber = item.hasNonNull("episodeNumber") ? item.get("episodeNumber").asInt() : null;
                result.add(new EpisodeMeta(episodeNumber, title));
            }
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private record EpisodeMeta(Integer episodeNumber, String title) {
    }

    private String normalizeIncomingPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String normalized = raw.trim().replace("\\", "/");
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            int idx = normalized.indexOf("/storage/");
            if (idx >= 0) {
                return normalized.substring(idx);
            }
            int uploads = normalized.indexOf("/uploads/");
            if (uploads >= 0) {
                return "/storage/" + normalized.substring(uploads + "/uploads/".length());
            }
            return normalized;
        }
        if (normalized.startsWith("/storage/")) {
            return normalized;
        }
        if (normalized.startsWith("/uploads/")) {
            return "/storage/" + normalized.substring("/uploads/".length());
        }
        if (normalized.startsWith("storage/")) {
            return "/" + normalized;
        }
        if (normalized.startsWith("uploads/")) {
            return "/storage/" + normalized.substring("uploads/".length());
        }
        return normalized;
    }

    private String storeFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            String original = file.getOriginalFilename() == null ? "file.bin" : file.getOriginalFilename();
            String sanitized = original.replaceAll("[^a-zA-Z0-9._-]", "_");
            String filename = UUID.randomUUID() + "_" + sanitized;
            Path dir = Path.of(uploadDir, folder).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return "/storage/" + folder + "/" + filename;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to store file: " + ex.getMessage(), ex);
        }
    }

    private void requireSeriesOwnerOrAdmin(Long seriesId, AuthenticatedUser user) {
        if (user != null && "admin".equalsIgnoreCase(user.role())) {
            return;
        }
        Long ownerId = jdbcTemplate.query(
                "SELECT TOP 1 UploaderID FROM Series WHERE SeriesID = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                seriesId
        );
        if (ownerId == null) {
            throw new NotFoundException("Story not found");
        }
        if (user == null || !ownerId.equals(user.userId())) {
            throw new UnauthorizedException("You do not own this story");
        }
    }

    private void requireChapterOwnerOrAdmin(Long chapterId, AuthenticatedUser user) {
        if (user != null && "admin".equalsIgnoreCase(user.role())) {
            return;
        }
        Long ownerId = jdbcTemplate.query(
                """
                SELECT TOP 1 s.UploaderID
                FROM Chapters c
                INNER JOIN Series s ON s.SeriesID = c.SeriesID
                WHERE c.ChapterID = ?
                """,
                rs -> rs.next() ? rs.getLong(1) : null,
                chapterId
        );
        if (ownerId == null) {
            throw new NotFoundException("Chapter not found");
        }
        if (user == null || !ownerId.equals(user.userId())) {
            throw new UnauthorizedException("You do not own this chapter");
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
}


