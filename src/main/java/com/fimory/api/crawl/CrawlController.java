package com.fimory.api.crawl;

import com.fimory.api.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class CrawlController {

    private final CrawlStoryService crawlStoryService;

    public CrawlController(CrawlStoryService crawlStoryService) {
        this.crawlStoryService = crawlStoryService;
    }

    @GetMapping("/crawl/movie/{tmdbId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawlMovieByTmdb(@PathVariable String tmdbId) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "tmdbId", tmdbId,
                "message", "TODO: implement TMDB crawl with TMDB_API_KEY"
        )));
    }

    @PostMapping("/crawl/movie")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawlMovie() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "TODO: implement movie crawl")));
    }

    @PostMapping("/crawl/story")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawlStory(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            String url = payload == null ? null : String.valueOf(payload.get("url"));
            boolean download = payload != null && payload.get("download") != null
                    && Boolean.parseBoolean(String.valueOf(payload.get("download")));
            return ResponseEntity.ok(ApiResponse.ok(crawlStoryService.crawlStory(url, download)));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(
                    false,
                    Map.of("ok", false, "error", ex.getMessage()),
                    Map.of()
            ));
        }
    }

    @PostMapping("/crawl/chapter")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawlChapter(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            Map<String, Object> body = payload == null ? new LinkedHashMap<>() : payload;
            return ResponseEntity.ok(ApiResponse.ok(crawlStoryService.crawlChapter(body)));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(
                    false,
                    Map.of("ok", false, "error", ex.getMessage()),
                    Map.of()
            ));
        }
    }

    @PostMapping("/crawl/cleanup-temp")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cleanupTemporaryAssets(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            Map<String, Object> body = payload == null ? new LinkedHashMap<>() : payload;
            return ResponseEntity.ok(ApiResponse.ok(crawlStoryService.cleanupTemporaryAssets(body)));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(
                    false,
                    Map.of("ok", false, "error", ex.getMessage()),
                    Map.of()
            ));
        }
    }

    @PostMapping("/admin/stories/{seriesId}/chapters/crawl")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawlAdminChapter(@PathVariable Long seriesId) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("seriesId", seriesId, "message", "TODO: implement admin chapter crawl")));
    }

    @PostMapping("/admin/movies/{movieId}/episodes/crawl")
    public ResponseEntity<ApiResponse<Map<String, Object>>> crawlAdminEpisode(@PathVariable Long movieId) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("movieId", movieId, "message", "TODO: implement admin episode crawl")));
    }
}
