package com.querylens.analyzer;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ParsedPlan {
    private PlanNode root;
    private double planningTimeMs;
    private double executionTimeMs;
    private long rowsReturned;
    private String rawJson;
    private List<PlanNode> flatNodes;
}
