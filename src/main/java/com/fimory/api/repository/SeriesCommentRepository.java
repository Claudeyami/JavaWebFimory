package com.fimory.api.repository;

import com.fimory.api.domain.SeriesCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeriesCommentRepository extends JpaRepository<SeriesCommentEntity, Long> {
    List<SeriesCommentEntity> findBySeriesIdOrderByCreatedAtDesc(Long seriesId);
}
