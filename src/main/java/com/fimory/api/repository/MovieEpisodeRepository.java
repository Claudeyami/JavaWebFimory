package com.fimory.api.repository;

import com.fimory.api.domain.MovieEpisodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieEpisodeRepository extends JpaRepository<MovieEpisodeEntity, Long> {
    List<MovieEpisodeEntity> findByMovieIdOrderByEpisodeNumberAsc(Long movieId);
}
