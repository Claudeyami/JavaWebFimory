package com.fimory.api.repository;

import com.fimory.api.domain.MovieRatingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MovieRatingRepository extends JpaRepository<MovieRatingEntity, Long> {
    List<MovieRatingEntity> findByMovieId(Long movieId);
    Optional<MovieRatingEntity> findByMovieIdAndUserId(Long movieId, Long userId);
}
