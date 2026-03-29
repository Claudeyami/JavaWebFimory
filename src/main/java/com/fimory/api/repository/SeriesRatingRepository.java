package com.fimory.api.repository;

import com.fimory.api.domain.SeriesRatingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeriesRatingRepository extends JpaRepository<SeriesRatingEntity, Long> {
    List<SeriesRatingEntity> findBySeriesId(Long seriesId);
    Optional<SeriesRatingEntity> findBySeriesIdAndUserId(Long seriesId, Long userId);
}
