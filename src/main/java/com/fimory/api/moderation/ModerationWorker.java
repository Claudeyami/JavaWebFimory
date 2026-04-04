package com.fimory.api.moderation;

import com.fimory.api.domain.SeriesEntity;
import com.fimory.api.repository.SeriesRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class ModerationWorker {

    private final ModerationJobRepository jobRepository;
    private final ModerationService moderationService;
    private final SeriesRepository seriesRepository;
    private final JdbcTemplate jdbcTemplate;
    private final String uploadDir;

    public ModerationWorker(ModerationJobRepository jobRepository,
                            ModerationService moderationService,
                            SeriesRepository seriesRepository,
                            JdbcTemplate jdbcTemplate,
                            @Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.jobRepository = jobRepository;
        this.moderationService = moderationService;
        this.seriesRepository = seriesRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.uploadDir = uploadDir;
    }

    @Scheduled(fixedDelay = 3000)
    public void processJobs() {
        List<ModerationJobEntity> jobs =
                jobRepository.findTop10ByStatusAndNextRetryAtBeforeOrderByPriorityDescCreatedAtAsc(
                        "Pending",
                        LocalDateTime.now().plusSeconds(1)
                );

        for (ModerationJobEntity job : jobs) {
            processJob(job);
        }
    }

    private void processJob(ModerationJobEntity job) {
        try {
            job.setStatus("Processing");
            job.setLastError(null);
            job.setLockedAt(LocalDateTime.now());
            jobRepository.save(job);

            if ("Series".equalsIgnoreCase(job.getContentType())) {
                handleSeriesJob(job);
            } else {
                job.setDecision("REVIEW");
                job.setStatus("Completed");
                job.setLastError("Unsupported content type for mock worker: " + job.getContentType());
            }

            job.setLockedAt(null);
            job.setLockedBy(null);
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);
            System.out.println(">>> [WORKER] Finished moderation job ID: " + job.getId()
                    + " | status=" + job.getStatus()
                    + " | decision=" + job.getDecision());
        } catch (Exception ex) {
            job.setStatus("Failed");
            job.setRetryCount((job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1);
            job.setLastError(ex.getMessage());
            job.setNextRetryAt(LocalDateTime.now().plusMinutes(5));
            job.setLockedAt(null);
            job.setLockedBy(null);
            job.setUpdatedAt(LocalDateTime.now());
            jobRepository.save(job);
            System.err.println(">>> [WORKER] Failed moderation job ID: " + job.getId() + " - " + ex.getMessage());
        }
    }

    private void handleSeriesJob(ModerationJobEntity job) {
        SeriesEntity series = seriesRepository.findById(job.getContentId())
                .orElseThrow(() -> new IllegalStateException("Series not found: " + job.getContentId()));
        List<ModerationEvidence> imageUrls = collectSeriesImages(series.getId(), series.getCoverUrl());
        if (shouldWaitForChapterImages(series.getId(), imageUrls, job)) {
            return;
        }

        clearModerationResults(job.getId());

        String textToScan = buildSeriesText(series);
        ModerationResult result = moderationService.scanText(textToScan);
        recordModerationResult(job.getId(), "SERIES_TEXT", "TEXT", "series:" + series.getId() + ":text", result);
        if ("BLOCK".equalsIgnoreCase(result.decision())) {
            flagSeriesForAdminReview(job, series, "BLOCK", result.reason());
            return;
        }

        List<ModerationTextEvidence> chapterTexts = collectSeriesChapterTexts(series.getId());
        for (ModerationTextEvidence evidence : chapterTexts) {
            ModerationResult chapterTextResult = moderationService.scanText(evidence.text());
            recordModerationResult(job.getId(), evidence.category(), "TEXT", evidence.path(), chapterTextResult);
            if ("BLOCK".equalsIgnoreCase(chapterTextResult.decision())) {
                flagSeriesForAdminReview(job, series, "BLOCK", chapterTextResult.reason());
                return;
            }
            if ("REVIEW".equalsIgnoreCase(chapterTextResult.decision())) {
                result = chapterTextResult;
            }
        }

        for (ModerationEvidence evidence : imageUrls) {
            ModerationResult imageResult = moderationService.scanImage(evidence.path());
            recordModerationResult(job.getId(), evidence.category(), "IMAGE", evidence.path(), imageResult);
            if ("BLOCK".equalsIgnoreCase(imageResult.decision())) {
                flagSeriesForAdminReview(job, series, "BLOCK", imageResult.reason());
                return;
            }
            if ("REVIEW".equalsIgnoreCase(imageResult.decision())) {
                result = imageResult;
            }
        }

        if ("REVIEW".equalsIgnoreCase(result.decision())) {
            flagSeriesForAdminReview(job, series, "REVIEW", result.reason());
            return;
        }

        clearSeriesModerationFlag(job, series, result.reason());
        job.setDecision(result.decision());
        job.setStatus("Completed");
        job.setLastError(normalizeReason(result.reason()));
    }

    private void flagSeriesForAdminReview(ModerationJobEntity job, SeriesEntity series, String decision, String reason) {
        String oldStatus = series.getStatus();
        String nextStatus = "BLOCK".equalsIgnoreCase(decision) ? "Rejected" : "Pending";
        String normalizedReason = normalizeReason(reason);
        series.setStatus(nextStatus);
        seriesRepository.save(series);
        syncSeriesModerationState(series.getId(), nextStatus);
        job.setDecision(decision);
        job.setStatus("Completed");
        job.setLastError(normalizedReason);
        notifyAdminsFlaggedByAi(series, decision, normalizedReason);
        insertActivityLog(series, oldStatus, decision, normalizedReason);
    }

    private void clearSeriesModerationFlag(ModerationJobEntity job, SeriesEntity series, String reason) {
        String currentStatus = safeText(series.getStatus());
        if (!"Pending".equalsIgnoreCase(currentStatus)) {
            return;
        }
        String normalizedReason = normalizeReason(reason);
        series.setStatus("Pending");
        seriesRepository.save(series);
        syncSeriesModerationState(series.getId(), "Pending");
        insertActivityLog(
                series,
                currentStatus,
                "ALLOW",
                normalizedReason.isBlank() ? "Latest moderation job cleared the previous flag." : normalizedReason
        );
    }

    private boolean shouldWaitForChapterImages(Long seriesId,
                                               List<ModerationEvidence> imageUrls,
                                               ModerationJobEntity job) {
        if (seriesId == null || job == null || !isComicSeries(seriesId)) {
            return false;
        }
        int chapterCount = countSeriesChapters(seriesId);
        if (chapterCount <= 0) {
            return false;
        }
        boolean hasChapterImage = imageUrls.stream()
                .anyMatch(item -> "CHAPTER_IMAGE".equalsIgnoreCase(item.category()));
        if (hasChapterImage) {
            return false;
        }
        int retryCount = job.getRetryCount() == null ? 0 : job.getRetryCount();
        int maxRetries = job.getMaxRetries() == null ? 0 : job.getMaxRetries();
        if (retryCount >= maxRetries) {
            return false;
        }
        job.setStatus("Pending");
        job.setDecision("NONE");
        job.setRetryCount(retryCount + 1);
        job.setNextRetryAt(LocalDateTime.now().plusSeconds(10));
        job.setLastError("Comic story has no chapter images yet. Waiting for chapter data before moderation.");
        return true;
    }

    private int countSeriesChapters(Long seriesId) {
        if (seriesId == null || !tableExists("Chapters")) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM Chapters WHERE SeriesID = ?",
                Integer.class,
                seriesId
        );
        return count == null ? 0 : count;
    }

    private List<ModerationTextEvidence> collectSeriesChapterTexts(Long seriesId) {
        List<ModerationTextEvidence> items = new ArrayList<>();
        if (seriesId == null || !tableExists("Chapters") || !columnExists("Chapters", "Content")) {
            return items;
        }

        List<ChapterTextSource> rows = jdbcTemplate.query(
                """
                SELECT TOP 3 ChapterID, ChapterNumber, Content
                FROM Chapters
                WHERE SeriesID = ?
                  AND Content IS NOT NULL
                  AND LTRIM(RTRIM(Content)) <> ''
                ORDER BY ChapterNumber ASC, ChapterID ASC
                """,
                (rs, rowNum) -> new ChapterTextSource(
                        rs.getLong("ChapterID"),
                        rs.getInt("ChapterNumber"),
                        rs.getString("Content")
                ),
                seriesId
        );

        for (ChapterTextSource row : rows) {
            String text = loadChapterText(row.contentPath());
            if (text.isBlank()) {
                continue;
            }
            items.add(new ModerationTextEvidence(
                    "CHAPTER_TEXT",
                    "series:" + seriesId + ":chapter:" + row.chapterNumber(),
                    text
            ));
        }
        return items;
    }

    private List<ModerationEvidence> collectSeriesImages(Long seriesId, String coverUrl) {
        List<ModerationEvidence> imageUrls = new ArrayList<>();
        if (coverUrl != null && !coverUrl.isBlank()) {
            imageUrls.add(new ModerationEvidence("SERIES_COVER", coverUrl.trim()));
        }

        if (!tableExists("ChapterImages")) {
            return imageUrls;
        }

        List<String> orderedChapterImages = jdbcTemplate.query(
                """
                SELECT ci.ImageURL
                FROM ChapterImages ci
                INNER JOIN Chapters c ON c.ChapterID = ci.ChapterID
                WHERE c.SeriesID = ?
                ORDER BY c.ChapterNumber ASC, ci.ImageOrder ASC, ci.ImageID ASC
                """,
                (rs, rowNum) -> rs.getString("ImageURL"),
                seriesId
        );

        List<String> chapterImages = sampleChapterImages(orderedChapterImages);
        for (String imageUrl : chapterImages) {
            if (imageUrl == null || imageUrl.isBlank()) {
                continue;
            }
            String normalized = imageUrl.trim();
            boolean exists = imageUrls.stream().anyMatch(item -> item.path().equals(normalized));
            if (!exists) {
                imageUrls.add(new ModerationEvidence("CHAPTER_IMAGE", normalized));
            }
        }
        return imageUrls;
    }

    private List<String> sampleChapterImages(List<String> orderedImageUrls) {
        List<String> normalized = new ArrayList<>();
        if (orderedImageUrls == null || orderedImageUrls.isEmpty()) {
            return normalized;
        }
        for (String imageUrl : orderedImageUrls) {
            if (imageUrl == null || imageUrl.isBlank()) {
                continue;
            }
            String value = imageUrl.trim();
            if (!normalized.contains(value)) {
                normalized.add(value);
            }
        }
        if (normalized.size() <= 5) {
            return normalized;
        }

        int total = normalized.size();
        int[] candidateIndexes = new int[]{
                0,
                Math.max(0, total / 4),
                Math.max(0, total / 2),
                Math.max(0, (total * 3) / 4),
                total - 1
        };

        List<String> sampled = new ArrayList<>();
        for (int index : candidateIndexes) {
            String value = normalized.get(Math.min(total - 1, Math.max(0, index)));
            if (!sampled.contains(value)) {
                sampled.add(value);
            }
        }
        return sampled;
    }

    private void clearModerationResults(Long jobId) {
        if (jobId == null || !tableExists("ContentModerationResults")) {
            return;
        }
        jdbcTemplate.update("DELETE FROM ContentModerationResults WHERE JobID = ?", jobId);
    }

    private void recordModerationResult(Long jobId,
                                        String category,
                                        String evidenceType,
                                        String evidencePath,
                                        ModerationResult result) {
        if (jobId == null || result == null || !tableExists("ContentModerationResults")) {
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO ContentModerationResults (JobID, Category, ConfidenceScore, EvidenceType, EvidencePath, RawResponse, CreatedAt)
                VALUES (?, ?, ?, ?, ?, ?, GETDATE())
                """,
                jobId,
                category,
                decisionToScore(result.decision()),
                evidenceType,
                evidencePath,
                "{\"decision\":\"" + safeJson(result.decision()) + "\",\"reason\":\"" + safeJson(result.reason()) + "\"}"
        );
    }

    private double decisionToScore(String decision) {
        if ("BLOCK".equalsIgnoreCase(decision)) {
            return 95.0d;
        }
        if ("REVIEW".equalsIgnoreCase(decision)) {
            return 70.0d;
        }
        return 5.0d;
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

    private void syncSeriesModerationState(Long seriesId, String status) {
        if (seriesId == null) {
            return;
        }
        if (columnExists("Series", "IsApproved")) {
            boolean approved = "Approved".equalsIgnoreCase(status);
            jdbcTemplate.update("UPDATE Series SET IsApproved = ? WHERE SeriesID = ?", approved, seriesId);
        }
        if (columnExists("Series", "ApprovedAt") && !"Approved".equalsIgnoreCase(status)) {
            jdbcTemplate.update("UPDATE Series SET ApprovedAt = NULL WHERE SeriesID = ?", seriesId);
        }
    }

    private String buildSeriesText(SeriesEntity series) {
        String title = series.getTitle() == null ? "" : series.getTitle().trim();
        String description = series.getDescription() == null ? "" : series.getDescription().trim();
        if (description.isBlank()) {
            return "Title: " + title;
        }
        return "Title: " + title + "\nDescription: " + description;
    }

    private boolean isComicSeries(Long seriesId) {
        if (seriesId == null || !columnExists("Series", "StoryType")) {
            return false;
        }
        String storyType = jdbcTemplate.query(
                "SELECT TOP 1 StoryType FROM Series WHERE SeriesID = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                seriesId
        );
        return storyType != null && "comic".equalsIgnoreCase(storyType.trim());
    }

    private String loadChapterText(String storedPath) {
        String normalized = safeText(storedPath);
        if (normalized.isBlank()) {
            return "";
        }
        try {
            Path filePath = resolveStoragePath(normalized);
            if (filePath == null || !Files.exists(filePath) || Files.isDirectory(filePath)) {
                return "";
            }
            return truncateText(Files.readString(filePath, StandardCharsets.UTF_8).trim(), 6000);
        } catch (Exception ignored) {
            return "";
        }
    }

    private Path resolveStoragePath(String storagePath) {
        String normalized = storagePath.replace("\\", "/").trim();
        if (!normalized.startsWith("/storage/")) {
            return null;
        }
        String relativePath = normalized.substring("/storage/".length());
        return Path.of(uploadDir).toAbsolutePath().normalize().resolve(relativePath).normalize();
    }

    private void notifyAdminsFlaggedByAi(SeriesEntity series, String decision, String reason) {
        String title = truncateText("AI phat hien noi dung can kiem tra", 120);
        String content = truncateText(
                "Truyen \"" + safeText(series.getTitle()) + "\" bi AI danh dau " + safeText(decision) + ". Ly do: " + safeText(reason),
                240
        );
        if (isDuplicateAdminModerationNotification(content)) {
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO Notifications (UserID, Type, Title, Content, RelatedURL, IsRead, CreatedAt)
                SELECT u.UserID, ?, ?, ?, ?, 0, GETDATE()
                FROM Users u
                INNER JOIN Roles r ON r.RoleID = u.RoleID
                WHERE UPPER(r.RoleName) = 'ADMIN'
                  AND (u.IsActive = 1 OR u.IsActive IS NULL)
                """,
                "ModerationFlagged",
                title,
                content,
                "/admin/stories"
        );
    }

    private void insertActivityLog(SeriesEntity series, String oldStatus, String decision, String reason) {
        jdbcTemplate.update(
                """
                INSERT INTO ActivityLogs (UserID, Action, TableName, RecordID, OldValue, NewValue, IPAddress, UserAgent, CreatedAt)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, GETDATE())
                """,
                null,
                "AI_MODERATION_FLAG",
                "Series",
                series.getId(),
                "{\"Status\":\"" + safeJson(oldStatus) + "\"}",
                "{\"Status\":\"" + safeJson(series.getStatus()) + "\",\"Decision\":\"" + safeJson(decision) + "\",\"Reason\":\"" + safeJson(reason) + "\"}",
                "127.0.0.1",
                "ModerationWorker/Gemini"
        );
    }

    private String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String normalizeReason(String reason) {
        String value = safeText(reason);
        if (value.isBlank()) {
            return "Moderation review required.";
        }
        return truncateText(value, 220);
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncateText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private boolean isDuplicateAdminModerationNotification(String content) {
        if (!tableExists("Notifications")) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1)
                FROM Notifications n
                INNER JOIN Users u ON u.UserID = n.UserID
                INNER JOIN Roles r ON r.RoleID = u.RoleID
                WHERE n.Type = 'ModerationFlagged'
                  AND n.Content = ?
                  AND n.CreatedAt >= DATEADD(MINUTE, -10, GETDATE())
                  AND UPPER(r.RoleName) = 'ADMIN'
                """,
                Integer.class,
                content
        );
        return count != null && count > 0;
    }

    private record ModerationEvidence(String category, String path) {
    }

    private record ModerationTextEvidence(String category, String path, String text) {
    }

    private record ChapterTextSource(Long chapterId, Integer chapterNumber, String contentPath) {
    }
}
