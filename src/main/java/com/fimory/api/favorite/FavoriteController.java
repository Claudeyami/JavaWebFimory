package com.fimory.api.favorite;

import com.fimory.api.common.ApiResponse;
import com.fimory.api.security.AuthenticatedUser;
import com.fimory.api.security.CurrentUserProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/favorites")
public class FavoriteController {

    private final CurrentUserProvider currentUserProvider;
    private final FavoriteService favoriteService;

    public FavoriteController(CurrentUserProvider currentUserProvider, FavoriteService favoriteService) {
        this.currentUserProvider = currentUserProvider;
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Long>>>> getFavorites(@RequestParam(required = false) String email) {
        String targetEmail = email;
        if (targetEmail == null || targetEmail.isBlank()) {
            targetEmail = currentUserProvider.requireUser().email();
        }
        List<Map<String, Long>> data = favoriteService.getMovieFavorites(targetEmail).stream()
                .map(movieId -> Map.of("MovieID", movieId))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> addFavorite(@RequestBody Map<String, Object> payload) {
        String email = payload.get("email") != null ? String.valueOf(payload.get("email")) : currentUserProvider.requireUser().email();
        Long movieId = payload.get("movieId") != null ? Long.valueOf(String.valueOf(payload.get("movieId"))) : null;
        if (movieId == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, Map.of("message", "movieId is required"), Map.of()));
        }
        favoriteService.addMovieFavorite(email, movieId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("success", true, "movieId", movieId)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteFavorite(@RequestParam(required = false) String email,
                                                                           @RequestParam(required = false) Long movieId) {
        String targetEmail = email;
        if (targetEmail == null || targetEmail.isBlank()) {
            AuthenticatedUser user = currentUserProvider.requireUser();
            targetEmail = user.email();
        }
        if (movieId == null) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, Map.of("message", "movieId is required"), Map.of()));
        }
        favoriteService.removeMovieFavorite(targetEmail, movieId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true, "movieId", movieId)));
    }

    @GetMapping("/user/series-favorites")
    public ResponseEntity<ApiResponse<List<Map<String, Long>>>> getSeriesFavorites() {
        String email = currentUserProvider.requireUser().email();
        List<Map<String, Long>> data = favoriteService.getSeriesFavorites(email).stream()
                .map(seriesId -> Map.of("SeriesID", seriesId))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
