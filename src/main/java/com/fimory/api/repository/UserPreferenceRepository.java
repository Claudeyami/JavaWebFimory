package com.fimory.api.repository;

import com.fimory.api.domain.UserPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPreferenceRepository extends JpaRepository<UserPreferenceEntity, Long> {
    Optional<UserPreferenceEntity> findByUserId(Long userId);
}
