package com.fimory.api.rating;

import com.fimory.api.common.ApiResponse;
import com.fimory.api.security.AuthenticatedUser;
import com.fimory.api.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class RatingController {

    private final CurrentUserProvider currentUserProvider;
    private final RatingService ratingService;

    public RatingController(CurrentUserProvider currentUserProvider, RatingService ratingService) {
        this.currentUserProvider = currentUserProvider;
        this.ratingService = ratingService;
    }

    @GetMapping("/movies/{id}/ratings")
    public ResponseEntity<ApiResponse<RatingSummaryDto>> movieRatings(@PathVariable Long id) {
        Long userId = null;
        try {
            userId = currentUserProvider.requireUser().userId();
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(ApiResponse.ok(ratingService.getMovieRatings(id, userId)));
    }

    @PostMapping("/movies/{id}/ratings")
    public ResponseEntity<ApiResponse<RatingSummaryDto>> rateMovie(@PathVariable Long id,
                                                                   @Valid @RequestBody RatingRequest request) {
        AuthenticatedUser user = currentUserProvider.requireUser();
        return ResponseEntity.ok(ApiResponse.ok(ratingService.rateMovie(id, user, request)));
    }

    @GetMapping("/stories/{id}/ratings")
    public ResponseEntity<ApiResponse<RatingSummaryDto>> storyRatings(@PathVariable Long id) {
        Long userId = null;
        try {
            userId = currentUserProvider.requireUser().userId();
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(ApiResponse.ok(ratingService.getSeriesRatings(id, userId)));
    }

    @PostMapping({"/stories/{id}/ratings", "/stories/{id}/rating"})
    public ResponseEntity<ApiResponse<RatingSummaryDto>> rateStory(@PathVariable Long id,
                                                                   @Valid @RequestBody RatingRequest request) {
        AuthenticatedUser user = currentUserProvider.requireUser();
        return ResponseEntity.ok(ApiResponse.ok(ratingService.rateSeries(id, user, request)));
    }
}
