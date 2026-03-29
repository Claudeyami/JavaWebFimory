package com.fimory.api.repository;

import com.fimory.api.domain.SeriesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeriesRepository extends JpaRepository<SeriesEntity, Long> {
    Optional<SeriesEntity> findBySlug(String slug);
}
