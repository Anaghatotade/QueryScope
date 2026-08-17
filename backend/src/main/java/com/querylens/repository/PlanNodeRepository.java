package com.querylens.repository;

import com.querylens.model.PlanNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlanNodeRepository extends JpaRepository<PlanNodeEntity, UUID> {
    List<PlanNodeEntity> findByPlanIdOrderBySortKeyAsc(UUID planId);
}
