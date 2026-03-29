package com.fimory.api.movie;

import com.fimory.api.common.ApiResponse;
import com.fimory.api.category.CategoryDto;
import com.fimory.api.favorite.FavoriteService;
import com.fimory.api.security.AuthenticatedUser;
import com.fimory.api.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class MovieController {

    private final MovieService movieService;
    private final CurrentUserProvider currentUserProvider;
    private final FavoriteService favoriteService;

    public MovieController(MovieService movieService,
                           CurrentUserProvider currentUserProvider,
                           FavoriteService favoriteService) {
        this.movieService = movieService;
        this.currentUserProvider = currentUserProvider;
        this.favoriteService = favoriteService;
    }

    @GetMapping("/movies")
    public ResponseEntity<ApiResponse<List<MovieDto>>> listMovies() {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok(movieService.getMovies()));
    }

    @GetMapping("/movies/{slug}")
    public ResponseEntity<ApiResponse<MovieDto>> movieDetail(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(movieService.getMovieBySlug(slug)));
    }

    @GetMapping("/movies/{slug}/episodes")
    public ResponseEntity<ApiResponse<List<MovieEpisodeDto>>> movieEpisodes(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(movieService.getEpisodesBySlug(slug)));
    }

    @GetMapping("/movies/search")
    public ResponseEntity<ApiResponse<List<MovieDto>>> searchMovies() {
        return ResponseEntity.ok(ApiResponse.ok(movieService.getMovies()));
    }

    @GetMapping("/movies/{movieId}/categories")
    public ResponseEntity<ApiResponse<List<CategoryDto>>> movieCategories(@PathVariable Long movieId) {
        return ResponseEntity.ok(ApiResponse.ok(movieService.getCategoriesByMovie(movieId)));
    }

    @GetMapping("/movies/{movieId}/tags")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> movieTags(@PathVariable Long movieId) {
        return ResponseEntity.ok(ApiResponse.ok(movieService.getTagsByMovie(movieId)));
    }

    @GetMapping("/movies/{movieId}/favorite-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> movieFavoriteStatus(@PathVariable Long movieId,
                                                                                 @RequestHeader(value = "x-user-email", required = false) String emailHeader) {
        String email = emailHeader;
        if (email == null || email.isBlank()) {
            try {
                email = currentUserProvider.requireUser().email();
            } catch (Exception ignored) {
                return ResponseEntity.ok(ApiResponse.ok(Map.of("isFavorite", false)));
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("isFavorite", favoriteService.isMovieFavorite(email, movieId))));
    }

    @PostMapping("/movies/{movieId}/favorite")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addMovieFavorite(@PathVariable Long movieId) {
        AuthenticatedUser user = currentUserProvider.requireUser();
        favoriteService.addMovieFavorite(user.email(), movieId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("success", true)));
    }

    @DeleteMapping("/movies/{movieId}/favorite")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeMovieFavorite(@PathVariable Long movieId) {
        AuthenticatedUser user = currentUserProvider.requireUser();
        favoriteService.removeMovieFavorite(user.email(), movieId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true)));
    }

    @PostMapping("/movies/auto-approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> autoApprove() {
        int updated = movieService.autoApprovePendingMovies();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "success", true,
                "updated", updated,
                "message", updated > 0 ? "Approved pending movies successfully" : "No pending movies to approve"
        )));
    }

    @DeleteMapping("/movies/delete/{movieId}")
    @PreAuthorize("hasAnyRole('ADMIN','UPLOADER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteMovieLegacy(@PathVariable Long movieId) {
        movieService.deleteMovie(movieId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("success", true, "deleted", true, "movieId", movieId)));
    }
}
