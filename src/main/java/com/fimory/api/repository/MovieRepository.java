package com.fimory.api.repository;

import com.fimory.api.domain.MovieEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MovieRepository extends JpaRepository<MovieEntity, Long> {
    Optional<MovieEntity> findBySlug(String slug);
}
