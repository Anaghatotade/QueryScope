package com.querylens.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlanParserService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ParsedPlan parse(String planJson) {
        try {
            JsonNode root = objectMapper.readTree(planJson);
            JsonNode planArray = root.isArray() ? root : objectMapper.createArrayNode().add(root);
            JsonNode top = planArray.get(0);
            JsonNode planNode = top.path("Plan");

            PlanNode rootNode = parseNode(planNode);
            List<PlanNode> flat = rootNode.flatten();

            return ParsedPlan.builder()
                    .root(rootNode)
                    .flatNodes(flat)
                    .planningTimeMs(top.path("Planning Time").asDouble(0))
                    .executionTimeMs(top.path("Execution Time").asDouble(0))
                    .rowsReturned(planNode.path("Actual Rows").asLong(0))
                    .rawJson(planJson)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse execution plan JSON", e);
        }
    }

    private PlanNode parseNode(JsonNode node) {
        List<PlanNode> children = new ArrayList<>();
        JsonNode plans = node.path("Plans");
        if (plans.isArray()) {
            for (JsonNode child : plans) {
                children.add(parseNode(child));
            }
        }

        long sharedHit = 0;
        long sharedRead = 0;
        JsonNode buffers = node.path("Shared Hit Blocks");
        if (buffers.isMissingNode()) {
            JsonNode bufferNode = node.path("Buffers");
            if (bufferNode.isObject()) {
                sharedHit = bufferNode.path("Shared Hit Blocks").asLong(0);
                sharedRead = bufferNode.path("Shared Read Blocks").asLong(0);
            }
        } else {
            sharedHit = buffers.asLong(0);
            sharedRead = node.path("Shared Read Blocks").asLong(0);
        }

        return PlanNode.builder()
                .nodeType(node.path("Node Type").asText("Unknown"))
                .relationName(textOrNull(node, "Relation Name"))
                .indexName(textOrNull(node, "Index Name"))
                .filter(textOrNull(node, "Filter"))
                .estimatedRows(node.path("Plan Rows").asDouble(0))
                .actualRows(node.path("Actual Rows").asLong(0))
                .startupCost(node.path("Startup Cost").asDouble(0))
                .totalCost(node.path("Total Cost").asDouble(0))
                .actualTimeMs(node.path("Actual Total Time").asDouble(0))
                .loops(node.path("Actual Loops").asInt(1))
                .sharedHitBlocks(sharedHit)
                .sharedReadBlocks(sharedRead)
                .children(children)
                .build();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
