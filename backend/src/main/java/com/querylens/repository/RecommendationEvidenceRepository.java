package com.querylens.repository;

import com.querylens.model.RecommendationEvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecommendationEvidenceRepository extends JpaRepository<RecommendationEvidenceEntity, UUID> {
    List<RecommendationEvidenceEntity> findByRecommendationIdOrderBySortOrderAsc(UUID recommendationId);
}
