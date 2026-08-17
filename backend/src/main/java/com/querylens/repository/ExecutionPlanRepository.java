package com.querylens.repository;

import com.querylens.model.ExecutionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionPlanRepository extends JpaRepository<ExecutionPlan, UUID> {
    Optional<ExecutionPlan> findFirstByExecution_Job_IdOrderByCapturedAtDesc(UUID jobId);
}
