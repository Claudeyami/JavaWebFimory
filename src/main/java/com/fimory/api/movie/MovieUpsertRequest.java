package com.fimory.api.movie;

import jakarta.validation.constraints.NotBlank;

public record MovieUpsertRequest(
        @NotBlank String slug,
        @NotBlank String title,
        String description,
        String coverUrl
) {
}
