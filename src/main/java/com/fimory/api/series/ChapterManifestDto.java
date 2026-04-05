package com.fimory.api.series;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChapterManifestDto(
        @JsonProperty("ChapterID") Long chapterId,
        @JsonProperty("ChapterNumber") Integer chapterNumber,
        @JsonProperty("Title") String title,
        @JsonProperty("IsFree") Boolean isFree,
        @JsonProperty("CreatedAt") String createdAt,
        @JsonProperty("ImageCount") Integer imageCount
) {
}
