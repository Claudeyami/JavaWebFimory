package com.fimory.api.series;

import jakarta.validation.constraints.NotBlank;

public record SeriesUpsertRequest(
        @NotBlank String slug,
        @NotBlank String title,
        String description,
        String coverUrl
) {
}
