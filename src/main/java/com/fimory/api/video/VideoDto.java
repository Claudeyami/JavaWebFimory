package com.fimory.api.video;

import java.time.Instant;

public record VideoDto(Long id,
                       String originalPath,
                       String hlsPath,
                       String status,
                       Instant createdAt,
                       String errorMessage) {
}
