package com.fimory.api.moderation;

import org.springframework.context.ApplicationEvent;

public class ModerationEvent extends ApplicationEvent {

    private final Long contentId;
    private final String contentType;

    public ModerationEvent(Object source, Long contentId, String contentType) {
        super(source);
        this.contentId = contentId;
        this.contentType = contentType;
    }

    public Long getContentId() {
        return contentId;
    }

    public String getContentType() {
        return contentType;
    }
}
