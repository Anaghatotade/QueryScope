package com.querylens.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PerformanceScore {
    private long overall;
    private double executionLatency;
    private double scanEfficiency;
    private double indexUtilization;
    private double cardinalityAccuracy;
    private double queryFrequency;
    private String grade;
}
