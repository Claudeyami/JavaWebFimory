package com.fimory.api.moderation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationJobService {

    private final ModerationJobRepository moderationJobRepository;

    public ModerationJobService(ModerationJobRepository moderationJobRepository) {
        this.moderationJobRepository = moderationJobRepository;
    }

    @Transactional
    public ModerationJobEntity createJob(Long contentId, String contentType) {
        return createJob(contentId, contentType, 1);
    }

    @Transactional
    public ModerationJobEntity createJob(Long contentId, String contentType, int priority) {
        ModerationJobEntity job = new ModerationJobEntity();
        job.setContentId(contentId);
        job.setContentType(contentType);
        job.setStatus("Pending");
        job.setDecision("NONE");
        job.setPriority(Math.max(0, priority));
        job.setRetryCount(0);
        job.setMaxRetries(3);
        return moderationJobRepository.save(job);
    }
}
