package com.fimory.api.moderation;

public interface ModerationService {

    ModerationResult scanText(String text);

    ModerationResult scanImage(String imageUrl);
}
