package com.fimory.api.comment;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record CommentDto(
        @JsonProperty("CommentID") Long commentId,
        @JsonProperty("UserID") Long userId,
        @JsonProperty("Email") String email,
        @JsonProperty("ParentCommentID") Long parentCommentId,
        @JsonProperty("Content") String content,
        @JsonProperty("CreatedAt") LocalDateTime createdAt,
        @JsonProperty("UpdatedAt") LocalDateTime updatedAt,
        @JsonProperty("IsDeleted") Boolean isDeleted,
        @JsonProperty("Username") String username,
        @JsonProperty("Avatar") String avatar,
        @JsonProperty("RoleName") String roleName,
        @JsonProperty("Level") Integer level,
        @JsonProperty("LikeCount") Integer likeCount,
        @JsonProperty("DislikeCount") Integer dislikeCount,
        @JsonProperty("UserReaction") String userReaction
) {
}
