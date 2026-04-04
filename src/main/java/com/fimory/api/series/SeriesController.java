package com.fimory.api.series;

import com.fimory.api.common.ApiResponse;
import com.fimory.api.category.CategoryDto;
import com.fimory.api.favorite.FavoriteService;
import com.fimory.api.movie.MovieService;
import com.fimory.api.rating.RatingService;
import com.fimory.api.repository.UserRepository;
import com.fimory.api.security.AuthenticatedUser;
import com.fimory.api.security.CurrentUserProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

@RestController
public class SeriesController {

    private final SeriesService seriesService;
    private final MovieService movieService;
    private final RatingService ratingService;
    private final CurrentUserProvider currentUserProvider;
    private final FavoriteService favoriteService;
    private final UserRepository userRepository;

    public SeriesController(SeriesService seriesService,
                            MovieService movieService,
                            RatingService ratingService,
                            CurrentUserProvider currentUserProvider,
                            FavoriteService favoriteService,
                            UserRepository userRepository) {
        this.seriesService = seriesService;
        this.movieService = movieService;
        this.ratingService = ratingService;
        this.currentUserProvider = currentUserProvider;
        this.favoriteService = favoriteService;
        this.userRepository = userRepository;
    }

    @GetMapping("/stories")
    public ResponseEntity<ApiResponse<List<SeriesDto>>> stories() {
        return ResponseEntity.ok(ApiResponse.ok(seriesService.getStories()));
    }

    @GetMapping("/stories/{slug}")
    public ResponseEntity<ApiResponse<SeriesDto>> detail(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(seriesService.getStoryBySlug(slug, currentUserOrNull())));
    }

    @GetMapping("/stories/{seriesId}/chapters")
    public ResponseEntity<ApiResponse<List<ChapterDto>>> chapters(@PathVariable Long seriesId) {
        return ResponseEntity.ok(ApiResponse.ok(seriesService.getChapters(seriesId, currentUserOrNull())));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Map<String, Object>>> search(@RequestParam(required = false, defaultValue = "") String q) {
        String keyword = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<Map<String, Object>> movies = movieService.getMovies().stream()
                .filter(m -> keyword.isBlank()
                        || containsIgnoreCase(m.title(), keyword)
                        || containsIgnoreCase(m.slug(), keyword))
                .limit(8)
                .map(m -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("MovieID", m.id());
                    row.put("Title", m.title());
                    row.put("Slug", m.slug());
                    row.put("PosterURL", m.coverUrl());
                    row.put("ReleaseYear", m.releaseYear());
                    row.put("Duration", m.duration());
                    row.put("Rating", m.rating());
                    row.put("TotalRatings", 0);
                    return row;
                })
                .toList();

        List<Map<String, Object>> series = seriesService.getStories().stream()
                .filter(s -> keyword.isBlank()
                        || containsIgnoreCase(s.title(), keyword)
                        || containsIgnoreCase(s.slug(), keyword))
                .limit(8)
                .map(s -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("SeriesID", s.id());
                    row.put("Title", s.title());
                    row.put("Slug", s.slug());
                    row.put("CoverURL", s.coverUrl());
                    row.put("Rating", 0);
                    row.put("TotalRatings", 0);
                    return row;
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "movies", movies,
                "series", series
        )));
    }

    @GetMapping("/stories/{seriesId}/categories")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> storyCategories(@PathVariable Long seriesId) {
        return ResponseEntity.ok(ApiResponse.ok(seriesService.getCategoriesBySeries(seriesId, currentUserOrNull())));
    }

    @GetMapping("/stories/{seriesId}/tags")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> storyTags(@PathVariable Long seriesId) {
        return ResponseEntity.ok(ApiResponse.ok(seriesService.getTagsBySeries(seriesId, currentUserOrNull())));
    }

    @GetMapping("/stories/{seriesId}/user-rating")
    public ResponseEntity<ApiResponse<Map<String, Object>>> storyUserRating(@PathVariable Long seriesId,
                                                                             @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        Long userId = null;
        try {
            userId = currentUserProvider.requireUser().userId();
        } catch (Exception ignored) {
        }
        if (userId == null && emailHeader != null && !emailHeader.isBlank()) {
            userId = userRepository.findByEmailIgnoreCase(emailHeader.trim())
                    .map(user -> user.getId())
                    .orElse(null);
        }
        Integer myRating = ratingService.getSeriesRatings(seriesId, userId).myRating();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rating", myRating == null ? 0 : myRating);
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    @GetMapping("/stories/{seriesId}/favorite-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> storyFavoriteStatus(@PathVariable Long seriesId,
                                                                                 @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        String email = emailHeader;
        if (email == null || email.isBlank()) {
            try {
                email = currentUserProvider.requireUser().email();
            } catch (Exception ignored) {
                return ResponseEntity.ok(ApiResponse.ok(Map.of("isFavorite", false)));
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("isFavorite", favoriteService.isSeriesFavorite(email, seriesId))));
    }

    @PostMapping("/stories/{seriesId}/favorite")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addStoryFavorite(@PathVariable Long seriesId,
                                                                              @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        String email = emailHeader;
        if (email == null || email.isBlank()) {
            AuthenticatedUser user = currentUserProvider.requireUser();
            email = user.email();
        }
        favoriteService.addSeriesFavorite(email, seriesId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("success", true)));
    }

    @DeleteMapping("/stories/{seriesId}/favorite")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeStoryFavorite(@PathVariable Long seriesId,
                                                                                 @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        String email = emailHeader;
        if (email == null || email.isBlank()) {
            AuthenticatedUser user = currentUserProvider.requireUser();
            email = user.email();
        }
        favoriteService.removeSeriesFavorite(email, seriesId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true)));
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private AuthenticatedUser currentUserOrNull() {
        try {
            return currentUserProvider.requireUser();
        } catch (Exception ignored) {
            return null;
        }
    }
}
