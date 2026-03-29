package com.fimory.api.category;

import jakarta.validation.constraints.NotBlank;

public record CategoryUpsertRequest(@NotBlank String name, String slug, String type) {
}
