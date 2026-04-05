package com.fimory.api.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/user")
public class UserUploadController {

    private final CurrentUserProvider currentUserProvider;
    private final MovieService movieService;
    private final SeriesService seriesService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String uploadDir;

    public UserUploadController(CurrentUserProvider currentUserProvider,
                                MovieService movieService,
                                SeriesService seriesService,
                                JdbcTemplate jdbcTemplate,
                                ObjectMapper objectMapper,
                                @Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.currentUserProvider = currentUserProvider;
        this.movieService = movieService;
        this.seriesService = seriesService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.uploadDir = uploadDir;
    }

    @GetMapping("/movies")
    public List<Map<String, Object>> userMovies() {
        AuthenticatedUser current = currentUserProvider.requireUser();
        String sql = """
                SELECT MovieID, Title, Description, PosterURL, Status, IsFree, CreatedAt
                FROM Movies
                WHERE UploaderID = ?
                ORDER BY CreatedAt DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("MovieID", rs.getLong("MovieID"));
            row.put("Title", rs.getString("Title"));
            row.put("Description", rs.getString("Description"));
            row.put("PosterURL", rs.getString("PosterURL"));
            row.put("Status", rs.getString("Status"));
            row.put("IsFree", rs.getObject("IsFree") != null && rs.getBoolean("IsFree"));
            row.put("CreatedAt", rs.getObject("CreatedAt"));
            return row;
        }, current.userId());
    }

    @GetMapping("/stories")
    public List<Map<String, Object>> userStories() {
        AuthenticatedUser current = currentUserProvider.requireUser();
        String sql = """
                SELECT SeriesID, Title, Description, CoverURL, Author, Status, IsFree, StoryType, CreatedAt
                FROM Series
                WHERE UploaderID = ?
                ORDER BY CreatedAt DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("SeriesID", rs.getLong("SeriesID"));
            row.put("StoryID", rs.getLong("SeriesID"));
            row.put("Title", rs.getString("Title"));
            row.put("Description", rs.getString("Description"));
            row.put("CoverURL", rs.getString("CoverURL"));
            row.put("Author", rs.getString("Author"));
            row.put("Status", rs.getString("Status"));
            row.put("IsFree", rs.getObject("IsFree") == null || rs.getBoolean("IsFree"));
            row.put("StoryType", rs.getString("StoryType"));
            row.put("CreatedAt", rs.getObject("CreatedAt"));
            return row;
        }, current.userId());
    }

    @PostMapping(value = "/movies", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createMovie(@RequestParam String title,
                                                                        @RequestParam(required = false) String description,
                                                                        @RequestParam(required = false) List<Long> categoryIds,
                                                                        @RequestParam(required = false) String episodes,
                                                                        @RequestPart(required = false) MultipartFile coverImage,
                                                                        @RequestPart(required = false) List<MultipartFile> episodeFiles) {
        AuthenticatedUser current = currentUserProvider.requireUser();
        String slug = slugify(title);
        String coverUrl = (coverImage != null && !coverImage.isEmpty()) ? storeFile(coverImage, "posters") : null;
        MovieDto created = movieService.createMovie(new MovieUpsertRequest(slug, title, description, coverUrl), current.userId(), categoryIds);

        List<EpisodeMeta> metas = parseEpisodeMeta(episodes);
        if (episodeFiles != null) {
            for (int i = 0; i < episodeFiles.size(); i++) {
                MultipartFile file = episodeFiles.get(i);
                if (file == null || file.isEmpty()) {
                    continue;
                }
                int episodeNo = i + 1;
                if (i < metas.size() && metas.get(i).episodeNumber() != null && metas.get(i).episodeNumber() > 0) {
                    episodeNo = metas.get(i).episodeNumber();
                }
                String episodeTitle = "Táº­p " + episodeNo;
                if (i < metas.size() && metas.get(i).title() != null && !metas.get(i).title().isBlank()) {
                    episodeTitle = metas.get(i).title().trim();
                }
                movieService.addEpisode(created.id(), episodeTitle, storeFile(file, "videos"));
            }
        }
        notifyAdmins(
                "NewContent",
                "CÃ³ phim má»›i chá» duyá»‡t",
                "NgÆ°á»i dÃ¹ng " + current.email() + " vá»«a upload phim \"" + created.title() + "\".",
                "/admin/movies"
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(Map.of(
                "success", true,
                "message", "Upload thÃ nh cÃ´ng. Phim Ä‘ang chá» duyá»‡t.",
                "movieId", created.id()
        )));
    }

    @PutMapping(value = "/movies/{movieId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateMovie(@PathVariable Long movieId,
                                                                        @RequestParam(required = false) String title,
                                                                        @RequestParam(required = false) String description,
                                                                        @RequestParam(required = false) List<Long> categoryIds,
                                                                        @RequestPart(required = false) MultipartFile coverImage) {
        AuthenticatedUser current = currentUserProvider.requireUser();
        requireMovieOwner(movieId, current.userId());
        Map<String, Object> currentMovie = jdbcTemplate.query(
                "SELECT TOP 1 Title, Description, Slug, PosterURL FROM Movies WHERE MovieID = ?",
                rs -> {
                    if (!rs.next()) return null;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("Title", rs.getString("Title"));
                    row.put("Description", rs.getString("Description"));
                    row.put("Slug", rs.getString("Slug"));
                    row.put("PosterURL", rs.getString("PosterURL"));
                    return row;
                },
                movieId
        );
        if (currentMovie == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, Map.of("error", "Movie not found"), Map.of()));
        }
        String nextTitle = title == null || title.isBlank() ? String.valueOf(currentMovie.get("Title")) : title;
        String nextDescription = description != null ? description : String.valueOf(currentMovie.get("Description"));
        String nextSlug = String.valueOf(currentMovie.get("Slug"));
        String nextCover = (coverImage != null && !coverImage.isEmpty()) ? storeFile(coverImage, "posters") : String.valueOf(currentMovie.get("PosterURL"));

        movieService.updateMovie(movieId, new MovieUpsertRequest(nextSlug, nextTitle, nextDescription, nextCover), categoryIds);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("success", true, "movieId", movieId)));
    }

    @DeleteMapping("/movies/{movieId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteMovie(@PathVariable Long movieId) {
        AuthenticatedUser current = currentUserProvider.requireUser();
        requireMovieOwner(movieId, current.userId());
        movieService.deleteMovie(movieId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true, "movieId", movieId)));
    }

    @GetMapping("/movies/{movieId}/episodes")
    public ResponseEntity<ApiResponse<List<?>>> userMovieEpisodes(@PathVariable Long movieId) {
        AuthenticatedUser current = currentUserProvider.requireUser();
        requireMovieOwner(movieId, current.userId());
        return ResponseEntity.ok(ApiResponse.ok(movieService.getEpisodesByMovieId(movieId)));
    }

    @PostMapping(value = "/movies/{movieId}/episodes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> addMovieEpisode(@PathVariable Long movieId,
                                                                             @RequestParam(required = false) String title,
                                                                             @RequestPart(required = false) MultipartFile videoFile) {
        AuthenticatedUser current = currentUserProvider.requireUser();
        requireMovieOwner(movieId, current.userId());
        String videoUrl = (videoFile != null && !videoFile.isEmpty()) ? storeFile(videoFile, "videos") : null;
        var episode = movieService.addEpisode(movieId, title, videoUrl);
        if (isMovieApproved(movieId)) {
            notifyMovieFavoritesNewEpisode(movieId, title);
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("movieId", movieId, "episode", episode)));
    }

    @PutMapping(value = "/episodes/{episodeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateMovieEpisode(@PathVariable Long episodeId,
                                                                                @RequestParam(required = false) String title,
                                                                                @RequestPart(required = false) MultipartFile videoFile) {
        AuthenticatedUser current = currentUserProvider.requireUser();
        requireEpisodeOwner(episodeId, current.userId());
        String videoUrl = (videoFile != null && !videoFile.isEmpty()) ? storeFile(videoFile, "videos") : null;
        var episode = movieService.updateEpisode(episodeId, title, videoUrl);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("episode", episode)));
    }

    @DeleteMapping("/episodes/{episodeId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteMovieEpisode(@PathVariable Long episodeId) {
        AuthenticatedUser current = currentUserProvider.requireUser();
        requireEpisodeOwner(episodeId, current.userId());
        movieService.deleteEpisode(episodeId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true, "episodeId", episodeId)));
    }

    @PostMapping(value = "/stories", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createStory(@RequestParam String title,
                                                                        @RequestParam(required = false) String description,
                                                                        @RequestParam(required = false) String author,
                                                                        @RequestParam(required = false) String storyType,
                                                                        @RequestParam(required = false) Boolean isFree,
                                                                        @RequestParam(required = false) List<Long> categoryIds,
                                                                        @RequestPart(required = false) MultipartFile coverImage) {
        AuthenticatedUser current = currentUserProvider.requireUser();
        String slug = slugify(title);
        String coverUrl = (coverImage != null && !coverImage.isEmpty()) ? storeFile(coverImage, "covers") : null;
        SeriesDto created = seriesService.createStory(new SeriesUpsertRequest(slug, title, description, coverUrl), current.userId(), categoryIds);
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
        notifyAdmins(
                "NewContent",
                "CÃ³ truyá»‡n má»›i chá» duyá»‡t",
                "NgÆ°á»i dÃ¹ng " + current.email() + " vá»«a upload truyá»‡n \"" + created.title() + "\".",
                "/admin/stories"
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(Map.of(
                "success", true,
                "message", "Upload thÃ nh cÃ´ng. Truyá»‡n Ä‘ang chá» duyá»‡t.",
                "seriesId", created.id()
        )));
    }

    @PutMapping(value = "/stories/{seriesId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateStory(@PathVariable Long seriesId,
                                                                        @RequestParam(required = false) String title,
                                                                        @RequestParam(required = false) String description,
                                                                        @RequestParam(required = false) String author,
                                                                        @RequestParam(required = false) String storyType,
                                                                        @RequestParam(required = false) Boolean isFree,
                                                                        @RequestParam(required = false) List<Long> categoryIds,
                                                                        @RequestPart(required = false) MultipartFile coverImage) {
        AuthenticatedUser current = currentUserProvider.requireUser();
        requireStoryOwner(seriesId, current.userId());
        Map<String, Object> row = jdbcTemplate.query(
                "SELECT TOP 1 Title, Description, Slug, CoverURL FROM Series WHERE SeriesID = ?",
                rs -> {
                    if (!rs.next()) return null;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("Title", rs.getString("Title"));
                    m.put("Description", rs.getString("Description"));
                    m.put("Slug", rs.getString("Slug"));
                    m.put("CoverURL", rs.getString("CoverURL"));
                    return m;
                },
                seriesId
        );
        if (row == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, Map.of("error", "Story not found"), Map.of()));
        }
        String nextTitle = title == null || title.isBlank() ? String.valueOf(row.get("Title")) : title;
        String nextDesc = description != null ? description : String.valueOf(row.get("Description"));
        String nextSlug = String.valueOf(row.get("Slug"));
        String nextCover = (coverImage != null && !coverImage.isEmpty()) ? storeFile(coverImage, "covers") : String.valueOf(row.get("CoverURL"));
        seriesService.updateStory(seriesId, new SeriesUpsertRequest(nextSlug, nextTitle, nextDesc, nextCover), categoryIds);
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
                seriesId
        );
        return ResponseEntity.ok(ApiResponse.ok(Map.of("success", true, "seriesId", seriesId)));
    }

    @DeleteMapping("/stories/{seriesId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteStory(@PathVariable Long seriesId) {
        AuthenticatedUser current = currentUserProvider.requireUser();
        requireStoryOwner(seriesId, current.userId());
        seriesService.deleteStory(seriesId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true, "seriesId", seriesId)));
    }

    @PostMapping(value = "/stories/{seriesId}/chapters", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> addStoryChapter(@PathVariable Long seriesId,
                                                                             @RequestParam(required = false) String title,
                                                                             @RequestParam(required = false) String storyType,
                                                                             @RequestPart(required = false) MultipartFile contentFile,
                                                                             @RequestPart(required = false) List<MultipartFile> chapterImages) {
        AuthenticatedUser current = currentUserProvider.requireUser();
        requireStoryOwner(seriesId, current.userId());

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

        if (isSeriesApproved(seriesId)) {
            notifySeriesFavoritesNewChapter(seriesId, chapterTitle, chapterNumber);
        }

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "seriesId", seriesId,
                "chapterId", chapterId,
                "chapterNumber", chapterNumber,
                "title", chapterTitle,
                "imageCount", imageCount
        )));
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
        for (String imagePath : imagePaths) {
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

    private void requireMovieOwner(Long movieId, Long userId) {
        Long owner = jdbcTemplate.query(
                "SELECT TOP 1 UploaderID FROM Movies WHERE MovieID = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                movieId
        );
        if (owner == null) {
            throw new NotFoundException("Movie not found");
        }
        if (!owner.equals(userId)) {
            throw new UnauthorizedException("You do not own this movie");
        }
    }

    private void requireStoryOwner(Long seriesId, Long userId) {
        Long owner = jdbcTemplate.query(
                "SELECT TOP 1 UploaderID FROM Series WHERE SeriesID = ?",
                rs -> rs.next() ? rs.getLong(1) : null,
                seriesId
        );
        if (owner == null) {
            throw new NotFoundException("Story not found");
        }
        if (!owner.equals(userId)) {
            throw new UnauthorizedException("You do not own this story");
        }
    }

    private void requireEpisodeOwner(Long episodeId, Long userId) {
        Long owner = jdbcTemplate.query(
                """
                SELECT TOP 1 m.UploaderID
                FROM MovieEpisodes e
                INNER JOIN Movies m ON m.MovieID = e.MovieID
                WHERE e.EpisodeID = ?
                """,
                rs -> rs.next() ? rs.getLong(1) : null,
                episodeId
        );
        if (owner == null) {
            throw new NotFoundException("Episode not found");
        }
        if (!owner.equals(userId)) {
            throw new UnauthorizedException("You do not own this movie episode");
        }
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

    private String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "content-" + System.currentTimeMillis();
        }
        String slug = value.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-");
        return slug.isBlank() ? ("content-" + System.currentTimeMillis()) : slug;
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

    private void notifyAdmins(String type, String title, String content, String relatedUrl) {
        jdbcTemplate.update(
                """
                INSERT INTO Notifications (UserID, Type, Title, Content, RelatedURL, IsRead, CreatedAt)
                SELECT u.UserID, ?, ?, ?, ?, 0, GETDATE()
                FROM Users u
                INNER JOIN Roles r ON r.RoleID = u.RoleID
                WHERE r.RoleName = 'Admin'
                  AND u.IsActive = 1
                """,
                type,
                title,
                content,
                relatedUrl
        );
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


