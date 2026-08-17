package com.querylens.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querylens.analyzer.ExplainService;
import com.querylens.analyzer.ParsedPlan;
import com.querylens.analyzer.PlanNode;
import com.querylens.model.AnalysisJob;
import com.querylens.model.OptimizationTestEntity;
import com.querylens.repository.AnalysisJobRepository;
import com.querylens.repository.OptimizationTestRepository;
import com.querylens.repository.RecommendationRepository;
import com.querylens.model.RecommendationEntity;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class VerificationService {

    private final RecommendationRepository recommendationRepository;
    private final AnalysisJobRepository jobRepository;
    private final OptimizationTestRepository testRepository;
    private final ExplainService explainService;
    private final JdbcTemplate targetJdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VerificationService(
            RecommendationRepository recommendationRepository,
            AnalysisJobRepository jobRepository,
            OptimizationTestRepository testRepository,
            ExplainService explainService,
            JdbcTemplate targetJdbc) {
        this.recommendationRepository = recommendationRepository;
        this.jobRepository = jobRepository;
        this.testRepository = testRepository;
        this.explainService = explainService;
        this.targetJdbc = targetJdbc;
    }

    @Transactional
    public VerificationResult verify(UUID recommendationId) {
        RecommendationEntity recommendation = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found"));

        if (recommendation.getSuggestedSql() == null
                || !recommendation.getSuggestedSql().toUpperCase().contains("CREATE INDEX")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Verification is only supported for CREATE INDEX recommendations");
        }

        AnalysisJob job = jobRepository.findById(recommendation.getJobId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis job not found"));

        String originalSql = job.getOriginalSql();
        String indexSql = recommendation.getSuggestedSql().replace(";", "").trim();

        try {
            targetJdbc.execute("SET transaction_read_only = OFF");
            ParsedPlan baseline = explainService.explainAnalyze(originalSql);
            long baselineScanned = countSeqScanRows(baseline.getRoot());

            targetJdbc.execute("BEGIN");
            targetJdbc.execute(indexSql);
            ParsedPlan optimized = explainService.explainAnalyze(originalSql);
            long optimizedScanned = countSeqScanRows(optimized.getRoot());

            double improvement = baseline.getExecutionTimeMs() <= 0 ? 0 :
                    ((baseline.getExecutionTimeMs() - optimized.getExecutionTimeMs()) / baseline.getExecutionTimeMs()) * 100;

            OptimizationTestEntity test = testRepository.save(OptimizationTestEntity.builder()
                    .recommendationId(recommendationId)
                    .baselineTimeMs(baseline.getExecutionTimeMs())
                    .optimizedTimeMs(optimized.getExecutionTimeMs())
                    .baselineRowsScanned(baselineScanned)
                    .optimizedRowsScanned(optimizedScanned)
                    .improvementPercent(improvement)
                    .testStatus("COMPLETED")
                    .testDetails(toJson(Map.of(
                            "method", "transaction_rollback",
                            "baseline_plan_root", baseline.getRoot().getNodeType(),
                            "optimized_plan_root", optimized.getRoot().getNodeType()
                    )))
                    .createdAt(Instant.now())
                    .build());

            recommendation.setVerified(improvement > 5 || optimizedScanned < baselineScanned);
            recommendationRepository.save(recommendation);

            return VerificationResult.builder()
                    .recommendationId(recommendationId)
                    .baselineTimeMs(baseline.getExecutionTimeMs())
                    .optimizedTimeMs(optimized.getExecutionTimeMs())
                    .baselineRowsScanned(baselineScanned)
                    .optimizedRowsScanned(optimizedScanned)
                    .improvementPercent(improvement)
                    .verified(recommendation.isVerified())
                    .testId(test.getId())
                    .build();
        } catch (Exception e) {
            testRepository.save(OptimizationTestEntity.builder()
                    .recommendationId(recommendationId)
                    .testStatus("FAILED")
                    .testDetails(toJson(Map.of("error", e.getMessage())))
                    .createdAt(Instant.now())
                    .build());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification failed: " + e.getMessage());
        } finally {
            try {
                targetJdbc.execute("ROLLBACK");
            } catch (Exception ignored) {
                // ensure connection is clean
            }
        }
    }

    private long countSeqScanRows(PlanNode node) {
        long total = 0;
        if ("Seq Scan".equalsIgnoreCase(node.getNodeType())) {
            total += node.getTotalActualRows();
        }
        for (PlanNode child : node.getChildren()) {
            total += countSeqScanRows(child);
        }
        return total;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
