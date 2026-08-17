package com.querylens.service;

import com.querylens.analyzer.ParsedPlan;
import com.querylens.model.QueryFingerprint;
import com.querylens.rules.RuleFinding;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PerformanceScoringService {

    public PerformanceScore score(ParsedPlan plan, List<RuleFinding> findings, QueryFingerprint fingerprint) {
        double latencyScore = scoreLatency(plan.getExecutionTimeMs());
        double scanScore = scoreScanEfficiency(plan);
        double indexScore = scoreIndexUtilization(plan);
        double cardinalityScore = scoreCardinality(findings);
        double frequencyScore = scoreFrequency(fingerprint.getExecutionCount());

        double weighted = latencyScore * 0.30
                + scanScore * 0.20
                + indexScore * 0.20
                + cardinalityScore * 0.10
                + frequencyScore * 0.10
                + scoreJoinEfficiency(plan) * 0.10;

        return PerformanceScore.builder()
                .overall(Math.round(weighted))
                .executionLatency(latencyScore)
                .scanEfficiency(scanScore)
                .indexUtilization(indexScore)
                .cardinalityAccuracy(cardinalityScore)
                .queryFrequency(frequencyScore)
                .grade(grade(weighted))
                .build();
    }

    private double scoreLatency(double ms) {
        if (ms <= 10) return 100;
        if (ms <= 50) return 85;
        if (ms <= 200) return 65;
        if (ms <= 1000) return 40;
        if (ms <= 5000) return 20;
        return 5;
    }

    private double scoreScanEfficiency(ParsedPlan plan) {
        long seqScans = plan.getFlatNodes().stream()
                .filter(n -> "Seq Scan".equalsIgnoreCase(n.getNodeType()))
                .count();
        if (seqScans == 0) return 100;
        if (seqScans == 1) return 45;
        return 15;
    }

    private double scoreIndexUtilization(ParsedPlan plan) {
        boolean hasIndexScan = plan.getFlatNodes().stream()
                .anyMatch(n -> n.getNodeType() != null && n.getNodeType().toLowerCase().contains("index"));
        return hasIndexScan ? 90 : 30;
    }

    private double scoreCardinality(List<RuleFinding> findings) {
        boolean hasIssue = findings.stream()
                .anyMatch(f -> "CARDINALITY_ESTIMATION".equals(f.ruleCode()));
        return hasIssue ? 35 : 95;
    }

    private double scoreFrequency(long count) {
        if (count <= 1) return 90;
        if (count <= 10) return 75;
        if (count <= 100) return 55;
        return 30;
    }

    private double scoreJoinEfficiency(ParsedPlan plan) {
        boolean expensiveJoin = plan.getFlatNodes().stream()
                .anyMatch(n -> n.getNodeType() != null
                        && n.getNodeType().toLowerCase().contains("join")
                        && n.getTotalActualTimeMs() > 100);
        return expensiveJoin ? 40 : 90;
    }

    private String grade(double score) {
        if (score >= 80) return "GOOD";
        if (score >= 60) return "FAIR";
        if (score >= 40) return "POOR";
        return "CRITICAL";
    }
}

@Data
@Builder
class PerformanceScore {
    private long overall;
    private double executionLatency;
    private double scanEfficiency;
    private double indexUtilization;
    private double cardinalityAccuracy;
    private double queryFrequency;
    private String grade;
}
