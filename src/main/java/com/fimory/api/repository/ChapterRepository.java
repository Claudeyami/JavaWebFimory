package com.fimory.api.repository;

import com.fimory.api.domain.ChapterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChapterRepository extends JpaRepository<ChapterEntity, Long> {
    List<ChapterEntity> findBySeriesIdOrderByChapterNumberAsc(Long seriesId);
}
