package com.fimory.api.moderation;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ModerationListener {

    private final ModerationJobService moderationJobService;

    public ModerationListener(ModerationJobService moderationJobService) {
        this.moderationJobService = moderationJobService;
    }

    @EventListener
    public void handleModerationEvent(ModerationEvent event) {
        moderationJobService.createJob(event.getContentId(), event.getContentType());
    }
}
