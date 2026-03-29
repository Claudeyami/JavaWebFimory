package com.fimory.api.rating;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RatingRequest(@Min(1) @Max(5) Integer score) {

    @JsonCreator
    public RatingRequest(@JsonProperty("score") Integer score,
                         @JsonProperty("rating") Integer rating) {
        this(score != null ? score : rating);
    }
}
