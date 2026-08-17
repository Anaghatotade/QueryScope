package com.querylens.repository;

import com.querylens.model.OptimizationTestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OptimizationTestRepository extends JpaRepository<OptimizationTestEntity, UUID> {
    Optional<OptimizationTestEntity> findFirstByRecommendationIdOrderByCreatedAtDesc(UUID recommendationId);
}
