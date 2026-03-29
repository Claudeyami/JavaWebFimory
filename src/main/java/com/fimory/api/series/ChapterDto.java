package com.fimory.api.series;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ChapterDto(
        @JsonProperty("ChapterID") Long id,
        @JsonProperty("ChapterNumber") Integer chapterNumber,
        @JsonProperty("Title") String title,
        @JsonProperty("Content") String content,
        @JsonProperty("IsFree") Boolean isFree,
        @JsonProperty("CreatedAt") String createdAt,
        @JsonProperty("ViewCount") Integer viewCount,
        @JsonProperty("ImageCount") Integer imageCount,
        @JsonProperty("Images") List<ChapterImageDto> images
) {
}
