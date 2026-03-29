package com.fimory.api.series;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SeriesDto(
        @JsonProperty("SeriesID") Long id,
        @JsonProperty("Slug") String slug,
        @JsonProperty("Title") String title,
        @JsonProperty("Description") String description,
        @JsonProperty("CoverURL") String coverUrl,
        @JsonProperty("Status") String status,
        @JsonProperty("IsFree") Boolean isFree,
        @JsonProperty("UploaderID") Long uploaderId,
        @JsonProperty("UploaderName") String uploaderName,
        @JsonProperty("UploaderEmail") String uploaderEmail,
        @JsonProperty("UploaderRole") String uploaderRole,
        @JsonProperty("StoryType") String storyType,
        @JsonProperty("CreatedAt") String createdAt,
        @JsonProperty("UpdatedAt") String updatedAt,
        @JsonProperty("Author") String author,
        @JsonProperty("ViewCount") Integer viewCount,
        @JsonProperty("Rating") Double rating,
        @JsonProperty("TotalRatings") Integer totalRatings,
        @JsonProperty("CommentCount") Integer commentCount,
        @JsonProperty("LatestChapterNumber") Integer latestChapterNumber
) {
    public SeriesDto(Long id, String slug, String title, String description, String coverUrl) {
        this(
                id,
                slug,
                title,
                description,
                coverUrl,
                "Pending",
                Boolean.TRUE,
                null,
                null,
                null,
                null,
                "Text",
                null,
                null,
                null,
                0,
                0.0,
                0,
                0,
                0
        );
    }
}
