package com.fimory.api.series;

import com.fimory.api.common.NotFoundException;
import com.fimory.api.category.CategoryDto;
import com.fimory.api.domain.SeriesEntity;
import com.fimory.api.repository.ChapterRepository;
import com.fimory.api.repository.SeriesRepository;
import com.fimory.api.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;

@Service
public class SeriesService {

    private final SeriesRepository seriesRepository;
    private final ChapterRepository chapterRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Path uploadRoot;

    public SeriesService(SeriesRepository seriesRepository,
                         ChapterRepository chapterRepository,
                         JdbcTemplate jdbcTemplate,
                         @Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.seriesRepository = seriesRepository;
        this.chapterRepository = chapterRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public List<SeriesDto> getStories() {
        String sql = """
                SELECT s.SeriesID, s.Slug, s.Title, s.Description, s.CoverURL, s.Status, s.IsFree, s.StoryType,
                       s.Author, COALESCE(s.ViewCount, 0) AS ViewCount,
                       COALESCE(sr.avgRating, 0) AS Rating,
                       COALESCE(sr.totalRatings, 0) AS TotalRatings,
                       COALESCE(sc.commentCount, 0) AS CommentCount,
                       COALESCE(ch.latestChapterNumber, 0) AS LatestChapterNumber,
                       s.UploaderID, s.CreatedAt, s.UpdatedAt,
                       u.Username AS UploaderName, u.Email AS UploaderEmail, r.RoleName AS UploaderRole
                FROM Series s
                LEFT JOIN Users u ON u.UserID = s.UploaderID
                LEFT JOIN Roles r ON r.RoleID = u.RoleID
                LEFT JOIN (
                    SELECT SeriesID, AVG(CAST(Rating AS FLOAT)) AS avgRating, COUNT(1) AS totalRatings
                    FROM SeriesRatings
                    GROUP BY SeriesID
                ) sr ON sr.SeriesID = s.SeriesID
                LEFT JOIN (
                    SELECT SeriesID, COUNT(1) AS commentCount
                    FROM SeriesComments
                    GROUP BY SeriesID
                ) sc ON sc.SeriesID = s.SeriesID
                LEFT JOIN (
                    SELECT SeriesID, MAX(ChapterNumber) AS latestChapterNumber
                    FROM Chapters
                    GROUP BY SeriesID
                ) ch ON ch.SeriesID = s.SeriesID
                WHERE s.Status = 'Approved'
                ORDER BY s.CreatedAt DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapSeriesRow(rs));
    }

    public List<SeriesDto> getStoriesForAdmin() {
        boolean hasModerationJobs = tableExists("ContentModerationJobs");
        String sql = """
                SELECT s.SeriesID, s.Slug, s.Title, s.Description, s.CoverURL, s.Status, s.IsFree, s.StoryType,
                       s.Author, COALESCE(s.ViewCount, 0) AS ViewCount,
                       COALESCE(sr.avgRating, 0) AS Rating,
                       COALESCE(sr.totalRatings, 0) AS TotalRatings,
                       COALESCE(sc.commentCount, 0) AS CommentCount,
                       COALESCE(ch.latestChapterNumber, 0) AS LatestChapterNumber,
                       s.UploaderID, s.CreatedAt, s.UpdatedAt,
                       u.Username AS UploaderName, u.Email AS UploaderEmail, r.RoleName AS UploaderRole
                FROM Series s
                LEFT JOIN Users u ON u.UserID = s.UploaderID
                LEFT JOIN Roles r ON r.RoleID = u.RoleID
                LEFT JOIN (
                    SELECT SeriesID, AVG(CAST(Rating AS FLOAT)) AS avgRating, COUNT(1) AS totalRatings
                    FROM SeriesRatings
                    GROUP BY SeriesID
                ) sr ON sr.SeriesID = s.SeriesID
                LEFT JOIN (
                    SELECT SeriesID, COUNT(1) AS commentCount
                    FROM SeriesComments
                    GROUP BY SeriesID
                ) sc ON sc.SeriesID = s.SeriesID
                LEFT JOIN (
                    SELECT SeriesID, MAX(ChapterNumber) AS latestChapterNumber
                    FROM Chapters
                    GROUP BY SeriesID
                ) ch ON ch.SeriesID = s.SeriesID
                """;
        if (hasModerationJobs) {
            sql += """
                    OUTER APPLY (
                        SELECT TOP 1 JobID, Status AS ModerationStatus, Decision AS ModerationDecision
                        FROM ContentModerationJobs
                        WHERE ContentType = 'Series' AND ContentID = s.SeriesID
                        ORDER BY CreatedAt DESC, JobID DESC
                    ) mj
                    WHERE UPPER(COALESCE(r.RoleName, '')) = 'ADMIN'
                       OR s.Status <> 'Pending'
                       OR (COALESCE(mj.ModerationStatus, '') = 'Completed' AND COALESCE(mj.ModerationDecision, 'NONE') = 'ALLOW')
                    ORDER BY s.CreatedAt DESC
                    """;
        } else {
            sql += " ORDER BY s.CreatedAt DESC";
        }
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapSeriesRow(rs));
    }

    public SeriesDto getStoryBySlug(String slug) {
        return getStoryBySlug(slug, null);
    }

    public SeriesDto getStoryBySlug(String slug, AuthenticatedUser viewer) {
        SeriesEntity series = seriesRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Story not found"));
        assertStoryVisible(series, viewer);

        String sql = """
                SELECT TOP 1 s.SeriesID, s.Slug, s.Title, s.Description, s.CoverURL, s.Status, s.IsFree, s.StoryType,
                       s.Author, COALESCE(s.ViewCount, 0) AS ViewCount,
                       COALESCE(sr.avgRating, 0) AS Rating,
                       COALESCE(sr.totalRatings, 0) AS TotalRatings,
                       COALESCE(sc.commentCount, 0) AS CommentCount,
                       COALESCE(ch.latestChapterNumber, 0) AS LatestChapterNumber,
                       s.UploaderID, s.CreatedAt, s.UpdatedAt,
                       u.Username AS UploaderName, u.Email AS UploaderEmail, r.RoleName AS UploaderRole
                FROM Series s
                LEFT JOIN Users u ON u.UserID = s.UploaderID
                LEFT JOIN Roles r ON r.RoleID = u.RoleID
                LEFT JOIN (
                    SELECT SeriesID, AVG(CAST(Rating AS FLOAT)) AS avgRating, COUNT(1) AS totalRatings
                    FROM SeriesRatings
                    GROUP BY SeriesID
                ) sr ON sr.SeriesID = s.SeriesID
                LEFT JOIN (
                    SELECT SeriesID, COUNT(1) AS commentCount
                    FROM SeriesComments
                    GROUP BY SeriesID
                ) sc ON sc.SeriesID = s.SeriesID
                LEFT JOIN (
                    SELECT SeriesID, MAX(ChapterNumber) AS latestChapterNumber
                    FROM Chapters
                    GROUP BY SeriesID
                ) ch ON ch.SeriesID = s.SeriesID
                WHERE s.Slug = ?
                """;
        List<SeriesDto> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new SeriesDto(
                rs.getLong("SeriesID"),
                rs.getString("Slug"),
                rs.getString("Title"),
                rs.getString("Description"),
                rs.getString("CoverURL"),
                rs.getString("Status") == null ? "Pending" : rs.getString("Status"),
                rs.getObject("IsFree") == null || rs.getBoolean("IsFree"),
                rs.getObject("UploaderID") != null ? rs.getLong("UploaderID") : null,
                rs.getString("UploaderName"),
                rs.getString("UploaderEmail"),
                rs.getString("UploaderRole"),
                rs.getString("StoryType") == null ? "Text" : rs.getString("StoryType"),
                rs.getObject("CreatedAt") != null ? String.valueOf(rs.getObject("CreatedAt")) : null,
                rs.getObject("UpdatedAt") != null ? String.valueOf(rs.getObject("UpdatedAt")) : null,
                rs.getString("Author"),
                rs.getObject("ViewCount") == null ? 0 : rs.getInt("ViewCount"),
                rs.getObject("Rating") == null ? 0.0 : rs.getDouble("Rating"),
                rs.getObject("TotalRatings") == null ? 0 : rs.getInt("TotalRatings"),
                rs.getObject("CommentCount") == null ? 0 : rs.getInt("CommentCount"),
                rs.getObject("LatestChapterNumber") == null ? 0 : rs.getInt("LatestChapterNumber")
        ), slug);
        if (rows.isEmpty()) {
            throw new NotFoundException("Story not found");
        }
        return rows.getFirst();
    }

    public List<ChapterDto> getChapters(Long seriesId) {
        return getChapters(seriesId, null);
    }

    public List<ChapterDto> getChapters(Long seriesId, AuthenticatedUser viewer) {
        SeriesEntity series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new NotFoundException("Story not found"));
        assertStoryVisible(series, viewer);

        boolean hasContent = columnExists("Chapters", "Content");
        boolean hasIsFree = columnExists("Chapters", "IsFree");
        boolean hasCreatedAt = columnExists("Chapters", "CreatedAt");
        boolean hasViewCount = columnExists("Chapters", "ViewCount");
        boolean hasImageCount = columnExists("Chapters", "ImageCount");
        boolean hasChapterImages = tableExists("ChapterImages");

        StringBuilder sql = new StringBuilder("""
                SELECT ChapterID, ChapterNumber, Title
                """);
        if (hasContent) sql.append(", Content");
        if (hasIsFree) sql.append(", IsFree");
        if (hasCreatedAt) sql.append(", CreatedAt");
        if (hasViewCount) sql.append(", ViewCount");
        if (hasImageCount) sql.append(", ImageCount");
        sql.append(" FROM Chapters WHERE SeriesID = ? ORDER BY ChapterNumber ASC, ChapterID ASC");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Long chapterId = rs.getLong("ChapterID");
            List<ChapterImageDto> images = List.of();
            if (hasChapterImages) {
                images = jdbcTemplate.query(
                        "SELECT ImageID, ImageURL, ImageOrder FROM ChapterImages WHERE ChapterID = ? ORDER BY ImageOrder ASC, ImageID ASC",
                        (imgRs, idx) -> new ChapterImageDto(
                                imgRs.getLong("ImageID"),
                                imgRs.getString("ImageURL"),
                                imgRs.getInt("ImageOrder")
                        ),
                        chapterId
                );
            }

            Integer imageCount = null;
            if (hasImageCount) {
                imageCount = rs.getObject("ImageCount") != null ? rs.getInt("ImageCount") : null;
            }
            if (imageCount == null && !images.isEmpty()) {
                imageCount = images.size();
            }

            return new ChapterDto(
                    chapterId,
                    rs.getInt("ChapterNumber"),
                    rs.getString("Title"),
                    hasContent ? rs.getString("Content") : null,
                    hasIsFree ? (rs.getObject("IsFree") == null ? null : rs.getBoolean("IsFree")) : null,
                    hasCreatedAt && rs.getObject("CreatedAt") != null ? String.valueOf(rs.getObject("CreatedAt")) : null,
                    hasViewCount ? (rs.getObject("ViewCount") == null ? null : rs.getInt("ViewCount")) : null,
                    imageCount,
                    images
            );
        }, seriesId);
    }

    public List<SeriesDto> getStoriesByCategory(Long categoryId) {
        String sql = """
                SELECT s.SeriesID, s.Slug, s.Title, s.Description, s.CoverURL
                FROM Series s
                INNER JOIN SeriesCategories sc ON sc.SeriesID = s.SeriesID
                WHERE sc.CategoryID = ?
                  AND s.Status = 'Approved'
                ORDER BY s.CreatedAt DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SeriesDto(
                rs.getLong("SeriesID"),
                rs.getString("Slug"),
                rs.getString("Title"),
                rs.getString("Description"),
                rs.getString("CoverURL")
        ), categoryId);
    }

    public List<CategoryDto> getCategoriesBySeries(Long seriesId) {
        return getCategoriesBySeries(seriesId, null);
    }

    public List<CategoryDto> getCategoriesBySeries(Long seriesId, AuthenticatedUser viewer) {
        SeriesEntity series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new NotFoundException("Story not found"));
        assertStoryVisible(series, viewer);

        String nameColumn = resolveCategoryNameColumn();
        String sql = "SELECT c.CategoryID, c." + nameColumn + " AS CategoryName, c.Slug " +
                "FROM Categories c INNER JOIN SeriesCategories sc ON sc.CategoryID = c.CategoryID " +
                "WHERE sc.SeriesID = ? ORDER BY c.CategoryID";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CategoryDto(
                rs.getLong("CategoryID"),
                rs.getString("CategoryName"),
                rs.getString("Slug")
        ), seriesId);
    }

    public List<Map<String, String>> getTagsBySeries(Long seriesId) {
        return getTagsBySeries(seriesId, null);
    }

    public List<Map<String, String>> getTagsBySeries(Long seriesId, AuthenticatedUser viewer) {
        SeriesEntity series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new NotFoundException("Story not found"));
        assertStoryVisible(series, viewer);

        String sql = """
                SELECT t.TagID, t.TagName, t.Slug
                FROM Tags t
                INNER JOIN SeriesTags st ON st.TagID = t.TagID
                WHERE st.SeriesID = ?
                ORDER BY t.TagID
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> Map.of(
                "TagID", String.valueOf(rs.getLong("TagID")),
                "Name", rs.getString("TagName"),
                "Slug", rs.getString("Slug")
        ), seriesId);
    }

    @Transactional
    public SeriesDto createStory(SeriesUpsertRequest request) {
        return createStory(request, null, null);
    }

    @Transactional
    public SeriesDto createStory(SeriesUpsertRequest request, Long uploaderId, List<Long> categoryIds) {
        SeriesEntity entity = new SeriesEntity();
        apply(entity, request, uploaderId);
        SeriesEntity saved = seriesRepository.save(entity);
        replaceSeriesCategories(saved.getId(), categoryIds);
        return map(saved);
    }

    @Transactional
    public SeriesDto updateStory(Long seriesId, SeriesUpsertRequest request) {
        return updateStory(seriesId, request, null);
    }

    @Transactional
    public SeriesDto updateStory(Long seriesId, SeriesUpsertRequest request, List<Long> categoryIds) {
        SeriesEntity entity = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new NotFoundException("Story not found"));
        apply(entity, request, entity.getUploaderId());
        SeriesEntity saved = seriesRepository.save(entity);
        if (categoryIds != null) {
            replaceSeriesCategories(seriesId, categoryIds);
        }
        return map(saved);
    }

    @Transactional
    public void deleteStory(Long seriesId) {
        List<String> mediaToDelete = new ArrayList<>();
        mediaToDelete.addAll(jdbcTemplate.query(
                "SELECT CoverURL FROM Series WHERE SeriesID = ?",
                (rs, rowNum) -> rs.getString("CoverURL"),
                seriesId
        ));
        if (columnExists("Chapters", "Content")) {
            mediaToDelete.addAll(jdbcTemplate.query(
                    "SELECT Content FROM Chapters WHERE SeriesID = ?",
                    (rs, rowNum) -> rs.getString("Content"),
                    seriesId
            ));
        }
        if (tableExists("ChapterImages")) {
            mediaToDelete.addAll(jdbcTemplate.query(
                    "SELECT ci.ImageURL FROM ChapterImages ci INNER JOIN Chapters c ON c.ChapterID = ci.ChapterID WHERE c.SeriesID = ?",
                    (rs, rowNum) -> rs.getString("ImageURL"),
                    seriesId
            ));
        }

        jdbcTemplate.update("DELETE FROM SeriesHistory WHERE SeriesID = ?", seriesId);
        jdbcTemplate.update(
                """
                DELETE FROM SeriesHistory
                WHERE ChapterID IN (SELECT ChapterID FROM Chapters WHERE SeriesID = ?)
                """,
                seriesId
        );
        jdbcTemplate.update("DELETE FROM ChapterImages WHERE ChapterID IN (SELECT ChapterID FROM Chapters WHERE SeriesID = ?)", seriesId);
        jdbcTemplate.update("DELETE FROM Chapters WHERE SeriesID = ?", seriesId);
        jdbcTemplate.update("DELETE FROM SeriesCategories WHERE SeriesID = ?", seriesId);
        jdbcTemplate.update("DELETE FROM SeriesTags WHERE SeriesID = ?", seriesId);
        jdbcTemplate.update("DELETE FROM SeriesRatings WHERE SeriesID = ?", seriesId);
        jdbcTemplate.update("DELETE FROM SeriesFavorites WHERE SeriesID = ?", seriesId);
        jdbcTemplate.update("DELETE FROM SeriesComments WHERE SeriesID = ?", seriesId);
        seriesRepository.deleteById(seriesId);
        mediaToDelete.forEach(this::deleteStoredMediaFileQuietly);
    }

    private void apply(SeriesEntity entity, SeriesUpsertRequest request, Long uploaderId) {
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
            entity.setIsFree(Boolean.TRUE);
        }
    }

    private String ensureUniqueSlug(String requestedSlug, Long currentSeriesId) {
        if (requestedSlug == null || requestedSlug.isBlank()) {
            throw new IllegalArgumentException("Slug is required");
        }

        String baseSlug = requestedSlug.trim();
        String candidate = baseSlug;
        int suffix = 2;

        while (true) {
            var existing = seriesRepository.findBySlug(candidate);
            if (existing.isEmpty()) {
                return candidate;
            }
            if (currentSeriesId != null && currentSeriesId.equals(existing.get().getId())) {
                return candidate;
            }
            candidate = baseSlug + "-" + suffix;
            suffix++;
        }
    }

    private SeriesDto map(SeriesEntity series) {
        String storyType = jdbcTemplate.query(
                "SELECT TOP 1 StoryType FROM Series WHERE SeriesID = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                series.getId()
        );
        return new SeriesDto(
                series.getId(),
                series.getSlug(),
                series.getTitle(),
                series.getDescription(),
                series.getCoverUrl(),
                series.getStatus(),
                series.getIsFree(),
                series.getUploaderId(),
                null,
                null,
                null,
                (storyType == null || storyType.isBlank()) ? "Text" : storyType,
                null,
                null,
                null,
                0,
                0.0,
                0,
                0,
                0
        );
    }

    private SeriesDto mapSeriesRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SeriesDto(
                rs.getLong("SeriesID"),
                rs.getString("Slug"),
                rs.getString("Title"),
                rs.getString("Description"),
                rs.getString("CoverURL"),
                rs.getString("Status") == null ? "Pending" : rs.getString("Status"),
                rs.getObject("IsFree") == null || rs.getBoolean("IsFree"),
                rs.getObject("UploaderID") != null ? rs.getLong("UploaderID") : null,
                rs.getString("UploaderName"),
                rs.getString("UploaderEmail"),
                rs.getString("UploaderRole"),
                rs.getString("StoryType") == null ? "Text" : rs.getString("StoryType"),
                rs.getObject("CreatedAt") != null ? String.valueOf(rs.getObject("CreatedAt")) : null,
                rs.getObject("UpdatedAt") != null ? String.valueOf(rs.getObject("UpdatedAt")) : null,
                rs.getString("Author"),
                rs.getObject("ViewCount") == null ? 0 : rs.getInt("ViewCount"),
                rs.getObject("Rating") == null ? 0.0 : rs.getDouble("Rating"),
                rs.getObject("TotalRatings") == null ? 0 : rs.getInt("TotalRatings"),
                rs.getObject("CommentCount") == null ? 0 : rs.getInt("CommentCount"),
                rs.getObject("LatestChapterNumber") == null ? 0 : rs.getInt("LatestChapterNumber")
        );
    }

    @Transactional
    public void replaceSeriesCategories(Long seriesId, List<Long> categoryIds) {
        jdbcTemplate.update("DELETE FROM SeriesCategories WHERE SeriesID = ?", seriesId);
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        for (Long categoryId : new LinkedHashSet<>(categoryIds)) {
            if (categoryId == null) {
                continue;
            }
            jdbcTemplate.update(
                    "INSERT INTO SeriesCategories (SeriesID, CategoryID) VALUES (?, ?)",
                    seriesId,
                    categoryId
            );
        }
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

    private void deleteStoredMediaFileQuietly(String rawPath) {
        Path filePath = resolveStoredFilePath(rawPath);
        if (filePath == null) {
            return;
        }
        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                cleanupEmptyParentDirectories(filePath.getParent());
            }
        } catch (Exception ignored) {
        }
    }

    private void cleanupEmptyParentDirectories(Path startDir) {
        if (startDir == null) {
            return;
        }
        Path chaptersRoot = uploadRoot.resolve("chapters").normalize();
        Path coversRoot = uploadRoot.resolve("covers").normalize();

        Path current = startDir.toAbsolutePath().normalize();
        while (current != null && !current.equals(uploadRoot)) {
            if (!current.startsWith(chaptersRoot) && !current.startsWith(coversRoot)) {
                break;
            }
            try (var children = Files.list(current)) {
                if (children.findAny().isPresent()) {
                    break;
                }
            } catch (Exception ignored) {
                break;
            }

            try {
                Files.deleteIfExists(current);
            } catch (Exception ignored) {
                break;
            }
            current = current.getParent();
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

    private void assertStoryVisible(SeriesEntity series, AuthenticatedUser viewer) {
        if (series == null) {
            throw new NotFoundException("Story not found");
        }
        if (isApproved(series.getStatus())) {
            return;
        }
        if (viewer != null && (isAdmin(viewer) || viewerOwnsStory(viewer, series))) {
            return;
        }
        throw new NotFoundException("Story not found");
    }

    private boolean isApproved(String status) {
        return status != null && "approved".equalsIgnoreCase(status.trim());
    }

    private boolean isAdmin(AuthenticatedUser viewer) {
        return viewer.role() != null && "admin".equalsIgnoreCase(viewer.role().trim());
    }

    private boolean viewerOwnsStory(AuthenticatedUser viewer, SeriesEntity series) {
        return viewer.userId() != null
                && series.getUploaderId() != null
                && viewer.userId().equals(series.getUploaderId());
    }
}
