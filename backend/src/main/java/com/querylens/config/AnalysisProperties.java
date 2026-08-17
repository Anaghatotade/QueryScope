package com.querylens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "querylens.analysis")
public record AnalysisProperties(
        long statementTimeoutMs,
        long largeTableRowThreshold,
        double scanAmplificationThreshold,
        double cardinalityErrorThreshold
) {
}
