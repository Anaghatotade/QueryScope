package com.querylens.repository;

import com.querylens.model.AnalysisJob;
import com.querylens.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, UUID> {
    List<AnalysisJob> findTop20ByOrderByCreatedAtDesc();
    List<AnalysisJob> findByStatusOrderByCreatedAtDesc(JobStatus status);
}
