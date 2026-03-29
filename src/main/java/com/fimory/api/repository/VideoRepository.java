package com.fimory.api.repository;

import com.fimory.api.domain.VideoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<VideoEntity, Long> {
}
