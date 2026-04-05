package com.fimory.api.series;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ChapterImageDetailDto(
        @JsonProperty("ImageID") Long imageId,
        @JsonProperty("ImageURL") String imageUrl,
        @JsonProperty("ImageOrder") Integer imageOrder,
        @JsonProperty("Width") Integer width,
        @JsonProperty("Height") Integer height
) {
}
