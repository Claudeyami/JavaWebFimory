package com.fimory.api.movie;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MovieEpisodeDto(
        @JsonProperty("EpisodeID") Long id,
        @JsonProperty("EpisodeNumber") Integer episodeNumber,
        @JsonProperty("Title") String title,
        @JsonProperty("VideoURL") String videoUrl,
        @JsonProperty("Duration") Integer duration,
        @JsonProperty("ViewCount") Long viewCount
) {
}
