package com.fimory.api.series;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ChapterDetailDto(
        @JsonProperty("SeriesID") Long seriesId,
        @JsonProperty("SeriesSlug") String seriesSlug,
        @JsonProperty("SeriesTitle") String seriesTitle,
        @JsonProperty("StoryType") String storyType,
        @JsonProperty("ChapterID") Long chapterId,
        @JsonProperty("ChapterNumber") Integer chapterNumber,
        @JsonProperty("Title") String title,
        @JsonProperty("Content") String content,
        @JsonProperty("IsFree") Boolean isFree,
        @JsonProperty("CreatedAt") String createdAt,
        @JsonProperty("ViewCount") Integer viewCount,
        @JsonProperty("ImageCount") Integer imageCount,
        @JsonProperty("Images") List<ChapterImageDetailDto> images,
        @JsonProperty("PrevChapterNumber") Integer prevChapterNumber,
        @JsonProperty("NextChapterNumber") Integer nextChapterNumber
) {
}
