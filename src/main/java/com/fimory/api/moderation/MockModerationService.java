package com.fimory.api.moderation;

import org.springframework.stereotype.Service;

@Service
public class MockModerationService implements ModerationService {

    @Override
    public ModerationResult scanText(String text) {
        if (text != null && text.contains("18+")) {
            return new ModerationResult("BLOCK", "Mock scan detected sensitive marker: 18+");
        }
        return new ModerationResult("ALLOW", "Mock scan marked content as safe");
    }

    @Override
    public ModerationResult scanImage(String imageUrl) {
        if (imageUrl != null && imageUrl.contains("18+")) {
            return new ModerationResult("BLOCK", "Mock image scan detected sensitive marker in image URL");
        }
        return new ModerationResult("ALLOW", "Mock image scan marked image as safe");
    }
}
