package com.querylens.service;

import com.querylens.analyzer.PlanNode;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AnalysisResult {
    private UUID jobId;
    private UUID fingerprintId;
    private String normalizedSql;
    private String fingerprintHash;
    private double executionTimeMs;
    private double planningTimeMs;
    private long rowsReturned;
    private PlanNode planRoot;
    private UUID planId;
    private PerformanceScore performanceScore;
    private List<RecommendationSummary> recommendations;
}
