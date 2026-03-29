package com.fimory.api.comment;

import com.fimory.api.common.ApiResponse;
import com.fimory.api.security.AuthenticatedUser;
import com.fimory.api.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class CommentController {

    private final CurrentUserProvider currentUserProvider;
    private final CommentService commentService;

    public CommentController(CurrentUserProvider currentUserProvider, CommentService commentService) {
        this.currentUserProvider = currentUserProvider;
        this.commentService = commentService;
    }

    @GetMapping("/movies/{id}/comments")
    public ResponseEntity<ApiResponse<List<CommentDto>>> getMovieComments(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.getMovieComments(id)));
    }

    @PostMapping("/movies/{id}/comments")
    public ResponseEntity<ApiResponse<CommentDto>> createMovieComment(@PathVariable Long id,
                                                                      @Valid @RequestBody CommentRequest request) {
        AuthenticatedUser user = currentUserProvider.requireUser();
        return ResponseEntity.ok(ApiResponse.ok(commentService.createMovieComment(id, user, request)));
    }

    @PutMapping("/movies/{id}/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentDto>> updateMovieComment(@PathVariable Long id,
                                                                      @PathVariable Long commentId,
                                                                      @Valid @RequestBody CommentRequest request) {
        AuthenticatedUser user = currentUserProvider.requireUser();
        return ResponseEntity.ok(ApiResponse.ok(commentService.updateMovieComment(id, commentId, user, request)));
    }

    @DeleteMapping("/movies/{id}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteMovieComment(@PathVariable Long id, @PathVariable Long commentId) {
        AuthenticatedUser user = currentUserProvider.requireUser();
        commentService.deleteMovieComment(id, commentId, user);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true, "movieId", id, "commentId", commentId)));
    }

    @GetMapping("/stories/{id}/comments")
    public ResponseEntity<ApiResponse<List<CommentDto>>> getStoryComments(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(commentService.getSeriesComments(id)));
    }

    @PostMapping("/stories/{id}/comments")
    public ResponseEntity<ApiResponse<CommentDto>> createStoryComment(@PathVariable Long id,
                                                                      @Valid @RequestBody CommentRequest request) {
        AuthenticatedUser user = currentUserProvider.requireUser();
        return ResponseEntity.ok(ApiResponse.ok(commentService.createSeriesComment(id, user, request)));
    }

    @PutMapping("/stories/{id}/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentDto>> updateStoryComment(@PathVariable Long id,
                                                                      @PathVariable Long commentId,
                                                                      @Valid @RequestBody CommentRequest request) {
        AuthenticatedUser user = currentUserProvider.requireUser();
        return ResponseEntity.ok(ApiResponse.ok(commentService.updateSeriesComment(id, commentId, user, request)));
    }

    @DeleteMapping("/stories/{id}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteStoryComment(@PathVariable Long id, @PathVariable Long commentId) {
        AuthenticatedUser user = currentUserProvider.requireUser();
        commentService.deleteSeriesComment(id, commentId, user);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("deleted", true, "seriesId", id, "commentId", commentId)));
    }
}
