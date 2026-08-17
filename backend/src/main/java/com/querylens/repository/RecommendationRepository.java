package com.querylens.repository;

import com.querylens.model.RecommendationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecommendationRepository extends JpaRepository<RecommendationEntity, UUID> {
    List<RecommendationEntity> findByJobIdOrderByImpactScoreDesc(UUID jobId);
    List<RecommendationEntity> findTop20ByOrderByImpactScoreDesc();
}
