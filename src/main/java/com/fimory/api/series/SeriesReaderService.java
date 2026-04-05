package com.fimory.api.series;

import com.fimory.api.common.NotFoundException;
import com.fimory.api.security.AuthenticatedUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SeriesReaderService {

    private final SeriesService seriesService;
    private final JdbcTemplate jdbcTemplate;

    public SeriesReaderService(SeriesService seriesService, JdbcTemplate jdbcTemplate) {
        this.seriesService = seriesService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getChapterManifest(String slug, AuthenticatedUser viewer) {
        SeriesDto story = seriesService.getStoryBySlug(slug, viewer);
        boolean hasIsFree = columnExists("Chapters", "IsFree");
        boolean hasCreatedAt = columnExists("Chapters", "CreatedAt");
        boolean hasImageCount = columnExists("Chapters", "ImageCount");

        StringBuilder sql = new StringBuilder("""
                SELECT ChapterID, ChapterNumber, Title
                """);
        if (hasIsFree) sql.append(", IsFree");
        if (hasCreatedAt) sql.append(", CreatedAt");
        if (hasImageCount) sql.append(", ImageCount");
        sql.append(" FROM Chapters WHERE SeriesID = ? ORDER BY ChapterNumber ASC, ChapterID ASC");

        List<ChapterManifestDto> chapters = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new ChapterManifestDto(
                rs.getLong("ChapterID"),
                rs.getInt("ChapterNumber"),
                rs.getString("Title"),
                hasIsFree ? (rs.getObject("IsFree") == null ? null : rs.getBoolean("IsFree")) : null,
                hasCreatedAt && rs.getObject("CreatedAt") != null ? String.valueOf(rs.getObject("CreatedAt")) : null,
                hasImageCount ? (rs.getObject("ImageCount") == null ? null : rs.getInt("ImageCount")) : null
        ), story.id());

        return Map.of("story", story, "chapters", chapters);
    }

    public ChapterDetailDto getChapterDetail(String slug, Integer chapterNumber, AuthenticatedUser viewer) {
        if (chapterNumber == null || chapterNumber <= 0) {
            throw new NotFoundException("Chapter not found");
        }

        SeriesDto story = seriesService.getStoryBySlug(slug, viewer);
        boolean hasContent = columnExists("Chapters", "Content");
        boolean hasIsFree = columnExists("Chapters", "IsFree");
        boolean hasCreatedAt = columnExists("Chapters", "CreatedAt");
        boolean hasViewCount = columnExists("Chapters", "ViewCount");
        boolean hasImageCount = columnExists("Chapters", "ImageCount");
        boolean hasChapterImages = tableExists("ChapterImages");
        boolean hasWidth = columnExists("ChapterImages", "Width");
        boolean hasHeight = columnExists("ChapterImages", "Height");

        StringBuilder sql = new StringBuilder("""
                SELECT TOP 1 ChapterID, ChapterNumber, Title
                """);
        if (hasContent) sql.append(", Content");
        if (hasIsFree) sql.append(", IsFree");
        if (hasCreatedAt) sql.append(", CreatedAt");
        if (hasViewCount) sql.append(", ViewCount");
        if (hasImageCount) sql.append(", ImageCount");
        sql.append(" FROM Chapters WHERE SeriesID = ? AND ChapterNumber = ? ORDER BY ChapterID ASC");

        List<ChapterDetailDto> chapters = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            long chapterId = rs.getLong("ChapterID");
            List<ChapterImageDetailDto> images = List.of();
            if (hasChapterImages) {
                StringBuilder imageSql = new StringBuilder("""
                        SELECT ImageID, ImageURL, ImageOrder
                        """);
                if (hasWidth) imageSql.append(", Width");
                if (hasHeight) imageSql.append(", Height");
                imageSql.append(" FROM ChapterImages WHERE ChapterID = ? ORDER BY ImageOrder ASC, ImageID ASC");
                images = jdbcTemplate.query(imageSql.toString(), (imgRs, idx) -> new ChapterImageDetailDto(
                        imgRs.getLong("ImageID"),
                        imgRs.getString("ImageURL"),
                        imgRs.getInt("ImageOrder"),
                        hasWidth ? (imgRs.getObject("Width") == null ? null : imgRs.getInt("Width")) : null,
                        hasHeight ? (imgRs.getObject("Height") == null ? null : imgRs.getInt("Height")) : null
                ), chapterId);
            }

            Integer prevChapterNumber = jdbcTemplate.query(
                    """
                    SELECT TOP 1 ChapterNumber
                    FROM Chapters
                    WHERE SeriesID = ? AND ChapterNumber < ?
                    ORDER BY ChapterNumber DESC, ChapterID DESC
                    """,
                    resultSet -> resultSet.next() ? resultSet.getInt(1) : null,
                    story.id(), chapterNumber
            );

            Integer nextChapterNumber = jdbcTemplate.query(
                    """
                    SELECT TOP 1 ChapterNumber
                    FROM Chapters
                    WHERE SeriesID = ? AND ChapterNumber > ?
                    ORDER BY ChapterNumber ASC, ChapterID ASC
                    """,
                    resultSet -> resultSet.next() ? resultSet.getInt(1) : null,
                    story.id(), chapterNumber
            );

            Integer imageCount = hasImageCount ? (rs.getObject("ImageCount") == null ? null : rs.getInt("ImageCount")) : null;
            if (imageCount == null && !images.isEmpty()) {
                imageCount = images.size();
            }

            return new ChapterDetailDto(
                    story.id(),
                    story.slug(),
                    story.title(),
                    story.storyType(),
                    chapterId,
                    rs.getInt("ChapterNumber"),
                    rs.getString("Title"),
                    hasContent ? rs.getString("Content") : null,
                    hasIsFree ? (rs.getObject("IsFree") == null ? null : rs.getBoolean("IsFree")) : null,
                    hasCreatedAt && rs.getObject("CreatedAt") != null ? String.valueOf(rs.getObject("CreatedAt")) : null,
                    hasViewCount ? (rs.getObject("ViewCount") == null ? null : rs.getInt("ViewCount")) : null,
                    imageCount,
                    images,
                    prevChapterNumber,
                    nextChapterNumber
            );
        }, story.id(), chapterNumber);

        if (chapters.isEmpty()) {
            throw new NotFoundException("Chapter not found");
        }
        return chapters.getFirst();
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
