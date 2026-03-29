package com.fimory.api.comment;

import jakarta.validation.constraints.NotBlank;

public record CommentRequest(@NotBlank String content, Long parentCommentId) {
}
