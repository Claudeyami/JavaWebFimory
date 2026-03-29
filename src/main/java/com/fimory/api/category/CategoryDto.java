package com.fimory.api.category;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CategoryDto(
        @JsonProperty("CategoryID") Long id,
        @JsonProperty("CategoryName") String name,
        @JsonProperty("Slug") String slug,
        @JsonProperty("Type") String type
) {
    public CategoryDto(Long id, String name, String slug) {
        this(id, name, slug, "Both");
    }
}
