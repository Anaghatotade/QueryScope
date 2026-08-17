package com.querylens.service;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class VerificationResult {
    private UUID recommendationId;
    private UUID testId;
    private double baselineTimeMs;
    private double optimizedTimeMs;
    private long baselineRowsScanned;
    private long optimizedRowsScanned;
    private double improvementPercent;
    private boolean verified;
}
