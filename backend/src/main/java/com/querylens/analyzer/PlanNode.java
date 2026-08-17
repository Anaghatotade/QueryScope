package com.querylens.analyzer;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class PlanNode {
    private String nodeType;
    private String relationName;
    private String indexName;
    private String filter;
    private double estimatedRows;
    private long actualRows;
    private double startupCost;
    private double totalCost;
    private double actualTimeMs;
    private int loops;
    private long sharedHitBlocks;
    private long sharedReadBlocks;
    @Builder.Default
    private List<PlanNode> children = new ArrayList<>();

    public long getTotalActualRows() {
        return actualRows * Math.max(loops, 1);
    }

    public double getTotalActualTimeMs() {
        return actualTimeMs * Math.max(loops, 1);
    }

    public List<PlanNode> flatten() {
        List<PlanNode> nodes = new ArrayList<>();
        flattenInto(nodes);
        return nodes;
    }

    private void flattenInto(List<PlanNode> nodes) {
        nodes.add(this);
        for (PlanNode child : children) {
            child.flattenInto(nodes);
        }
    }
}
