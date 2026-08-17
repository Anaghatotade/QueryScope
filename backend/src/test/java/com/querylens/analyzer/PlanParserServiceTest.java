package com.querylens.analyzer;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PlanParserServiceTest {

    private final PlanParserService parser = new PlanParserService();

    @Test
    void parsesSequentialScanPlan() throws Exception {
        String json = new ClassPathResource("fixtures/seq_scan_plan.json")
                .getContentAsString(StandardCharsets.UTF_8);

        ParsedPlan plan = parser.parse(json);

        assertEquals(4820.45, plan.getExecutionTimeMs(), 0.01);
        assertEquals("Seq Scan", plan.getRoot().getNodeType());
        assertEquals("orders", plan.getRoot().getRelationName());
        assertEquals(43, plan.getRoot().getActualRows());
        assertTrue(plan.getFlatNodes().stream().anyMatch(n -> "Seq Scan".equals(n.getNodeType())));
    }
}
