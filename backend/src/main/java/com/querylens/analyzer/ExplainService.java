package com.querylens.analyzer;

import com.querylens.config.AnalysisProperties;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ExplainService {

    private final JdbcTemplate targetJdbc;
    private final AnalysisProperties properties;
    private final PlanParserService planParser;

    public ExplainService(JdbcTemplate targetJdbc, AnalysisProperties properties, PlanParserService planParser) {
        this.targetJdbc = targetJdbc;
        this.properties = properties;
        this.planParser = planParser;
    }

    public ParsedPlan explainAnalyze(String sql) {
        try {
            targetJdbc.execute("SET statement_timeout = " + properties.statementTimeoutMs());
            targetJdbc.execute("SET transaction_read_only = ON");

            String explainSql = "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql;
            List<Map<String, Object>> rows = targetJdbc.queryForList(explainSql);
            if (rows.isEmpty()) {
                throw new IllegalStateException("EXPLAIN returned no rows");
            }

            Object raw = rows.get(0).values().iterator().next();
            String json = extractJson(raw);
            return planParser.parse(json);
        } finally {
            try {
                targetJdbc.execute("SET transaction_read_only = OFF");
            } catch (Exception ignored) {
                // connection may already be closed
            }
        }
    }

    private String extractJson(Object raw) {
        if (raw == null) {
            throw new IllegalStateException("EXPLAIN returned null JSON");
        }
        if (raw instanceof String s) {
            return s;
        }
        if (raw instanceof PGobject pg) {
            return pg.getValue();
        }
        return raw.toString();
    }
}
