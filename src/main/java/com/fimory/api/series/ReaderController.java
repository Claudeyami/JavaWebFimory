package com.fimory.api.series;

import com.fimory.api.common.ApiResponse;
import com.fimory.api.security.AuthenticatedUser;
import com.fimory.api.security.CurrentUserProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ReaderController {

    private final SeriesReaderService seriesReaderService;
    private final CurrentUserProvider currentUserProvider;

    public ReaderController(SeriesReaderService seriesReaderService, CurrentUserProvider currentUserProvider) {
        this.seriesReaderService = seriesReaderService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/reader/stories/{slug}/manifest")
    public ResponseEntity<ApiResponse<Map<String, Object>>> manifest(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(seriesReaderService.getChapterManifest(slug, currentUserOrNull())));
    }

    @GetMapping("/reader/stories/{slug}/chapters/{chapterNumber}")
    public ResponseEntity<ApiResponse<ChapterDetailDto>> chapterDetail(@PathVariable String slug,
                                                                       @PathVariable Integer chapterNumber) {
        return ResponseEntity.ok(ApiResponse.ok(seriesReaderService.getChapterDetail(slug, chapterNumber, currentUserOrNull())));
    }

    private AuthenticatedUser currentUserOrNull() {
        try {
            return currentUserProvider.requireUser();
        } catch (Exception ignored) {
            return null;
        }
    }
}
