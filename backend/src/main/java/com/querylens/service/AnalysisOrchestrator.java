package com.querylens.service;

import com.querylens.analyzer.*;
import com.querylens.model.*;
import com.querylens.repository.*;
import com.querylens.rules.RuleEngine;
import com.querylens.rules.RuleFinding;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AnalysisOrchestrator {

    private final SqlSafetyService sqlSafetyService;
    private final QueryNormalizationService normalizationService;
    private final ExplainService explainService;
    private final RuleEngine ruleEngine;
    private final PerformanceScoringService scoringService;
    private final AnalysisJobRepository jobRepository;
    private final QueryFingerprintRepository fingerprintRepository;
    private final QueryExecutionRepository executionRepository;
    private final ExecutionPlanRepository planRepository;
    private final PlanNodeRepository planNodeRepository;
    private final RecommendationRepository recommendationRepository;
    private final RecommendationEvidenceRepository evidenceRepository;

    public AnalysisOrchestrator(
            SqlSafetyService sqlSafetyService,
            QueryNormalizationService normalizationService,
            ExplainService explainService,
            RuleEngine ruleEngine,
            PerformanceScoringService scoringService,
            AnalysisJobRepository jobRepository,
            QueryFingerprintRepository fingerprintRepository,
            QueryExecutionRepository executionRepository,
            ExecutionPlanRepository planRepository,
            PlanNodeRepository planNodeRepository,
            RecommendationRepository recommendationRepository,
            RecommendationEvidenceRepository evidenceRepository) {
        this.sqlSafetyService = sqlSafetyService;
        this.normalizationService = normalizationService;
        this.explainService = explainService;
        this.ruleEngine = ruleEngine;
        this.scoringService = scoringService;
        this.jobRepository = jobRepository;
        this.fingerprintRepository = fingerprintRepository;
        this.executionRepository = executionRepository;
        this.planRepository = planRepository;
        this.planNodeRepository = planNodeRepository;
        this.recommendationRepository = recommendationRepository;
        this.evidenceRepository = evidenceRepository;
    }

    @Transactional
    public AnalysisResult analyze(String sql) {
        sqlSafetyService.validateReadOnly(sql);

        String normalized = normalizationService.normalize(sql);
        String hash = normalizationService.fingerprint(normalized);

        QueryFingerprint fingerprint = fingerprintRepository.findByFingerprintHash(hash)
                .orElseGet(() -> QueryFingerprint.builder()
                        .fingerprintHash(hash)
                        .normalizedSql(normalized)
                        .firstSeen(Instant.now())
                        .lastSeen(Instant.now())
                        .executionCount(0)
                        .totalExecutionMs(0)
                        .avgExecutionMs(0)
                        .build());

        AnalysisJob job = AnalysisJob.builder()
                .fingerprint(fingerprint)
                .originalSql(sql.trim())
                .status(JobStatus.RUNNING)
                .createdAt(Instant.now())
                .build();
        job = jobRepository.save(job);

        try {
            ParsedPlan plan = explainService.explainAnalyze(sql.trim());

            fingerprint.setLastSeen(Instant.now());
            fingerprint.setExecutionCount(fingerprint.getExecutionCount() + 1);
            fingerprint.setTotalExecutionMs(fingerprint.getTotalExecutionMs() + plan.getExecutionTimeMs());
            fingerprint.setAvgExecutionMs(fingerprint.getTotalExecutionMs() / fingerprint.getExecutionCount());
            fingerprint = fingerprintRepository.save(fingerprint);

            QueryExecution execution = executionRepository.save(QueryExecution.builder()
                    .job(job)
                    .fingerprint(fingerprint)
                    .executionTimeMs(plan.getExecutionTimeMs())
                    .planningTimeMs(plan.getPlanningTimeMs())
                    .rowsReturned(plan.getRowsReturned())
                    .capturedAt(Instant.now())
                    .build());

            ExecutionPlan executionPlan = planRepository.save(ExecutionPlan.builder()
                    .execution(execution)
                    .planJson(plan.getRawJson())
                    .rootNodeType(plan.getRoot().getNodeType())
                    .totalCost(plan.getRoot().getTotalCost())
                    .capturedAt(Instant.now())
                    .build());

            persistPlanNodes(executionPlan.getId(), plan.getRoot(), null, new AtomicInteger());

            List<RuleFinding> findings = ruleEngine.evaluate(sql, normalized, plan);
            List<RecommendationEntity> recommendations = persistRecommendations(job.getId(), findings);

            PerformanceScore score = scoringService.score(plan, findings, fingerprint);

            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);

            return AnalysisResult.builder()
                    .jobId(job.getId())
                    .fingerprintId(fingerprint.getId())
                    .normalizedSql(normalized)
                    .fingerprintHash(hash)
                    .executionTimeMs(plan.getExecutionTimeMs())
                    .planningTimeMs(plan.getPlanningTimeMs())
                    .rowsReturned(plan.getRowsReturned())
                    .planRoot(plan.getRoot())
                    .planId(executionPlan.getId())
                    .performanceScore(score)
                    .recommendations(recommendations.stream().map(this::toSummary).toList())
                    .build();

        } catch (Exception e) {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
            throw e;
        }
    }

    private void persistPlanNodes(UUID planId, PlanNode node, UUID parentId, AtomicInteger sortKey) {
        PlanNodeEntity entity = planNodeRepository.save(PlanNodeEntity.builder()
                .planId(planId)
                .parentId(parentId)
                .nodeType(node.getNodeType())
                .relationName(node.getRelationName())
                .estimatedRows(node.getEstimatedRows())
                .actualRows(node.getActualRows())
                .startupCost(node.getStartupCost())
                .totalCost(node.getTotalCost())
                .actualTimeMs(node.getActualTimeMs())
                .loops(node.getLoops())
                .sharedHitBlocks(node.getSharedHitBlocks())
                .sharedReadBlocks(node.getSharedReadBlocks())
                .filter(node.getFilter())
                .indexName(node.getIndexName())
                .sortKey(sortKey.getAndIncrement())
                .build());

        for (PlanNode child : node.getChildren()) {
            persistPlanNodes(planId, child, entity.getId(), sortKey);
        }
    }

    private List<RecommendationEntity> persistRecommendations(UUID jobId, List<RuleFinding> findings) {
        List<RecommendationEntity> saved = new ArrayList<>();
        for (RuleFinding finding : findings) {
            RecommendationEntity rec = recommendationRepository.save(RecommendationEntity.builder()
                    .jobId(jobId)
                    .ruleCode(finding.ruleCode())
                    .recommendationType(finding.recommendationType())
                    .severity(finding.severity())
                    .confidence(finding.confidence())
                    .title(finding.title())
                    .description(finding.description())
                    .suggestedSql(finding.suggestedSql())
                    .impactScore(finding.impactScore())
                    .verified(false)
                    .createdAt(Instant.now())
                    .build());

            int order = 0;
            for (EvidenceItem item : finding.evidence()) {
                evidenceRepository.save(RecommendationEvidenceEntity.builder()
                        .recommendationId(rec.getId())
                        .evidenceKey(item.key())
                        .evidenceValue(item.value())
                        .sortOrder(order++)
                        .build());
            }
            saved.add(rec);
        }
        return saved;
    }

    private RecommendationSummary toSummary(RecommendationEntity entity) {
        List<RecommendationEvidenceEntity> evidence =
                evidenceRepository.findByRecommendationIdOrderBySortOrderAsc(entity.getId());
        return RecommendationSummary.builder()
                .id(entity.getId())
                .ruleCode(entity.getRuleCode())
                .type(entity.getRecommendationType())
                .severity(entity.getSeverity().name())
                .confidence(entity.getConfidence())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .suggestedSql(entity.getSuggestedSql())
                .impactScore(entity.getImpactScore())
                .verified(entity.isVerified())
                .evidence(evidence.stream()
                        .map(e -> new EvidenceSummary(e.getEvidenceKey(), e.getEvidenceValue()))
                        .toList())
                .build();
    }
}
