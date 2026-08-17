package com.querylens.rules;

import com.querylens.model.Severity;
import lombok.Builder;

import java.util.List;

@Builder
public record RuleFinding(
        String ruleCode,
        String recommendationType,
        Severity severity,
        double confidence,
        String title,
        String description,
        String suggestedSql,
        double impactScore,
        List<EvidenceItem> evidence
) {
}
