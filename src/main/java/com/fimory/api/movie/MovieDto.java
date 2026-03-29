package com.fimory.api.movie;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MovieDto(
        @JsonProperty("MovieID") Long id,
        @JsonProperty("Slug") String slug,
        @JsonProperty("Title") String title,
        @JsonProperty("Description") String description,
        @JsonProperty("PosterURL") String coverUrl,
        @JsonProperty("Status") String status,
        @JsonProperty("IsFree") Boolean isFree,
        @JsonProperty("ViewCount") Long viewCount,
        @JsonProperty("Rating") Double rating,
        @JsonProperty("TrailerURL") String trailerUrl,
        @JsonProperty("ReleaseYear") Integer releaseYear,
        @JsonProperty("Duration") Integer duration,
        @JsonProperty("Country") String country,
        @JsonProperty("Director") String director,
        @JsonProperty("Cast") String cast,
        @JsonProperty("UploaderID") Long uploaderId,
        @JsonProperty("UploaderName") String uploaderName,
        @JsonProperty("UploaderEmail") String uploaderEmail,
        @JsonProperty("UploaderRole") String uploaderRole,
        @JsonProperty("CreatedAt") String createdAt,
        @JsonProperty("UpdatedAt") String updatedAt
) {
    public MovieDto(Long id, String slug, String title, String description, String coverUrl) {
        this(
                id,
                slug,
                title,
                description,
                coverUrl,
                "Approved",
                true,
                0L,
                0.0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
