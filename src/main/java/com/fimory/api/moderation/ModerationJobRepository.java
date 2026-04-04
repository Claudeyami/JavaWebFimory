package com.fimory.api.moderation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ModerationJobRepository extends JpaRepository<ModerationJobEntity, Long> {

    List<ModerationJobEntity> findTop10ByStatusAndNextRetryAtBeforeOrderByPriorityDescCreatedAtAsc(
            String status,
            LocalDateTime now
    );
}
