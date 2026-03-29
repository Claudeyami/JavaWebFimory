package com.fimory.api.series;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChapterImageDto(
        @JsonProperty("ImageID") Long imageId,
        @JsonProperty("ImageURL") String imageUrl,
        @JsonProperty("ImageOrder") Integer imageOrder
) {
}

