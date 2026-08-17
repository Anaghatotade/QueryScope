package com.querylens.controller;

import com.querylens.analyzer.DatabaseMetadataService;
import com.querylens.model.AnalysisJob;
import com.querylens.model.QueryFingerprint;
import com.querylens.model.RecommendationEntity;
import com.querylens.repository.AnalysisJobRepository;
import com.querylens.repository.QueryFingerprintRepository;
import com.querylens.repository.RecommendationRepository;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final QueryFingerprintRepository fingerprintRepository;
    private final AnalysisJobRepository jobRepository;
    private final RecommendationRepository recommendationRepository;
    private final DatabaseMetadataService metadataService;

    public DashboardController(
            QueryFingerprintRepository fingerprintRepository,
            AnalysisJobRepository jobRepository,
            RecommendationRepository recommendationRepository,
            DatabaseMetadataService metadataService) {
        this.fingerprintRepository = fingerprintRepository;
        this.jobRepository = jobRepository;
        this.recommendationRepository = recommendationRepository;
        this.metadataService = metadataService;
    }

    @GetMapping("/overview")
    public ResponseEntity<OverviewResponse> overview() {
        List<QueryFingerprint> fingerprints = fingerprintRepository.findTop20ByOrderByTotalExecutionMsDesc();
        List<RecommendationEntity> recommendations = recommendationRepository.findTop20ByOrderByImpactScoreDesc();
        var stats = metadataService.getDatabaseStats();

        long slowQueries = fingerprints.stream().filter(f -> f.getAvgExecutionMs() > 200).count();
        long highPriority = recommendations.stream()
                .filter(r -> r.getSeverity().name().equals("HIGH") || r.getSeverity().name().equals("CRITICAL"))
                .count();

        double totalDbTimeSec = fingerprints.stream().mapToDouble(QueryFingerprint::getTotalExecutionMs).sum() / 1000.0;

        return ResponseEntity.ok(OverviewResponse.builder()
                .queriesAnalyzed(fingerprints.stream().mapToLong(QueryFingerprint::getExecutionCount).sum())
                .uniqueFingerprints(fingerprints.size())
                .slowQueries(slowQueries)
                .highPriorityIssues(highPriority)
                .totalDbTimeSeconds(totalDbTimeSec)
                .targetTableCount(stats.getTableCount())
                .targetEstimatedRows(stats.getEstimatedTotalRows())
                .build());
    }

    @GetMapping("/queries")
    public ResponseEntity<List<QueryLeaderboardItem>> queryLeaderboard() {
        List<QueryLeaderboardItem> items = fingerprintRepository.findTop20ByOrderByTotalExecutionMsDesc()
                .stream()
                .map(f -> QueryLeaderboardItem.builder()
                        .fingerprintId(f.getId())
                        .normalizedSql(truncate(f.getNormalizedSql(), 120))
                        .executions(f.getExecutionCount())
                        .avgMs(f.getAvgExecutionMs())
                        .totalMs(f.getTotalExecutionMs())
                        .lastSeen(f.getLastSeen())
                        .build())
                .toList();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<JobSummary>> recentJobs() {
        return ResponseEntity.ok(jobRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(j -> JobSummary.builder()
                        .jobId(j.getId())
                        .status(j.getStatus().name())
                        .sql(truncate(j.getOriginalSql(), 100))
                        .createdAt(j.getCreatedAt())
                        .completedAt(j.getCompletedAt())
                        .build())
                .toList());
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobSummary> getJob(@PathVariable UUID id) {
        AnalysisJob job = jobRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Job not found"));
        return ResponseEntity.ok(JobSummary.builder()
                .jobId(job.getId())
                .status(job.getStatus().name())
                .sql(job.getOriginalSql())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .build());
    }

    @GetMapping("/recommendations")
    public ResponseEntity<List<RecommendationEntity>> topRecommendations() {
        return ResponseEntity.ok(recommendationRepository.findTop20ByOrderByImpactScoreDesc());
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    @Data
    @Builder
    public static class OverviewResponse {
        private long queriesAnalyzed;
        private int uniqueFingerprints;
        private long slowQueries;
        private long highPriorityIssues;
        private double totalDbTimeSeconds;
        private long targetTableCount;
        private long targetEstimatedRows;
    }

    @Data
    @Builder
    public static class QueryLeaderboardItem {
        private UUID fingerprintId;
        private String normalizedSql;
        private long executions;
        private double avgMs;
        private double totalMs;
        private Instant lastSeen;
    }

    @Data
    @Builder
    public static class JobSummary {
        private UUID jobId;
        private String status;
        private String sql;
        private String errorMessage;
        private Instant createdAt;
        private Instant completedAt;
    }
}
