package com.fimory.api.moderation;

public record ModerationResult(
        String decision,
        String reason
) {
}
