package com.querylens.repository;

import com.querylens.model.QueryExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QueryExecutionRepository extends JpaRepository<QueryExecution, UUID> {
    Optional<QueryExecution> findFirstByJobIdOrderByCapturedAtDesc(UUID jobId);
}
