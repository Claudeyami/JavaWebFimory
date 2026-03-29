package com.fimory.api.repository;

import com.fimory.api.domain.MovieCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieCommentRepository extends JpaRepository<MovieCommentEntity, Long> {
    List<MovieCommentEntity> findByMovieIdOrderByCreatedAtDesc(Long movieId);
}
