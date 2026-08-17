package com.querylens.service;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RecommendationSummary {
    private UUID id;
    private String ruleCode;
    private String type;
    private String severity;
    private double confidence;
    private String title;
    private String description;
    private String suggestedSql;
    private double impactScore;
    private boolean verified;
    private List<EvidenceSummary> evidence;
}
