package com.querylens.rules;

import com.querylens.analyzer.DatabaseMetadataService;
import com.querylens.analyzer.DatabaseMetadataService.IndexInfo;
import com.querylens.analyzer.ParsedPlan;
import com.querylens.analyzer.PlanNode;
import com.querylens.config.AnalysisProperties;
import com.querylens.model.Severity;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleEngine {

    private static final Pattern WHERE_COLUMN = Pattern.compile(
            "(\\w+)\\s*(=|>|<|>=|<=|<>|!=|LIKE|ILIKE|IN|IS)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SELECT_STAR = Pattern.compile("SELECT\\s+\\*", Pattern.CASE_INSENSITIVE);

    private final AnalysisProperties properties;
    private final DatabaseMetadataService metadataService;

    public RuleEngine(AnalysisProperties properties, DatabaseMetadataService metadataService) {
        this.properties = properties;
        this.metadataService = metadataService;
    }

    public List<RuleFinding> evaluate(String originalSql, String normalizedSql, ParsedPlan plan) {
        List<RuleFinding> findings = new ArrayList<>();
        findings.addAll(checkSequentialScans(originalSql, plan));
        findings.addAll(checkCardinalityMismatch(plan));
        findings.addAll(checkSelectStar(originalSql, plan));
        findings.addAll(checkExpensiveJoins(plan));
        findings.sort(Comparator.comparingDouble(RuleFinding::impactScore).reversed());
        return findings;
    }

    private List<RuleFinding> checkSequentialScans(String originalSql, ParsedPlan plan) {
        List<RuleFinding> findings = new ArrayList<>();

        for (PlanNode node : plan.getFlatNodes()) {
            if (!"Seq Scan".equalsIgnoreCase(node.getNodeType())) {
                continue;
            }

            String table = node.getRelationName();
            long tableRows = metadataService.getTableRowEstimate(table);
            long rowsReturned = Math.max(node.getTotalActualRows(), 1);
            long rowsScanned = Math.max(tableRows, node.getTotalActualRows());
            double amplification = (double) rowsScanned / Math.max(rowsReturned, 1);

            if (tableRows < properties.largeTableRowThreshold() || amplification < properties.scanAmplificationThreshold()) {
                continue;
            }

            List<String> filterColumns = extractFilterColumns(node.getFilter());
            if (filterColumns.isEmpty()) {
                filterColumns = extractWhereColumns(originalSql, table);
            }

            List<IndexInfo> indexes = metadataService.getIndexesForTable(table);
            boolean hasMatchingIndex = indexes.stream()
                    .anyMatch(idx -> indexCoversColumns(idx.getIndexDefinition(), filterColumns));

            if (hasMatchingIndex) {
                continue;
            }

            String suggestedIndex = buildIndexSuggestion(table, filterColumns);
            List<EvidenceItem> evidence = new ArrayList<>();
            evidence.add(ev("table", table));
            evidence.add(ev("table_rows_estimate", String.valueOf(tableRows)));
            evidence.add(ev("rows_returned", String.valueOf(rowsReturned)));
            evidence.add(ev("scan_amplification", String.format("%.1fx", amplification)));
            evidence.add(ev("access_path", "Sequential Scan"));
            evidence.add(ev("filter_columns", String.join(", ", filterColumns)));
            evidence.add(ev("existing_indexes", formatIndexes(indexes)));

            double impact = plan.getExecutionTimeMs() * Math.log10(amplification + 1);

            findings.add(RuleFinding.builder()
                    .ruleCode("SEQ_SCAN_HIGH_SELECTIVITY")
                    .recommendationType("MISSING_INDEX")
                    .severity(Severity.HIGH)
                    .confidence(Math.min(0.95, 0.6 + (amplification / 10000)))
                    .title("High scan amplification on " + table)
                    .description("The query scans approximately " + formatNumber(rowsScanned)
                            + " rows to return " + formatNumber(rowsReturned)
                            + ". A composite index may reduce scanned rows substantially.")
                    .suggestedSql(suggestedIndex)
                    .impactScore(impact)
                    .evidence(evidence)
                    .build());
        }

        return findings;
    }

    private List<RuleFinding> checkCardinalityMismatch(ParsedPlan plan) {
        List<RuleFinding> findings = new ArrayList<>();

        for (PlanNode node : plan.getFlatNodes()) {
            double estimated = node.getEstimatedRows();
            long actual = node.getTotalActualRows();
            if (estimated <= 0 || actual <= 0) {
                continue;
            }

            double ratio = actual / estimated;
            if (ratio < properties.cardinalityErrorThreshold() && ratio > 1.0 / properties.cardinalityErrorThreshold()) {
                continue;
            }

            List<EvidenceItem> evidence = List.of(
                    ev("node_type", node.getNodeType()),
                    ev("relation", Objects.toString(node.getRelationName(), "N/A")),
                    ev("estimated_rows", String.valueOf((long) estimated)),
                    ev("actual_rows", String.valueOf(actual)),
                    ev("estimation_error", String.format("%.1fx", Math.max(ratio, 1 / ratio)))
            );

            findings.add(RuleFinding.builder()
                    .ruleCode("CARDINALITY_ESTIMATION")
                    .recommendationType("STATISTICS")
                    .severity(Severity.MEDIUM)
                    .confidence(0.75)
                    .title("Cardinality estimation mismatch")
                    .description("PostgreSQL estimated " + (long) estimated + " rows but processed "
                            + actual + ". Consider running ANALYZE or increasing statistics targets.")
                    .suggestedSql(node.getRelationName() != null
                            ? "ANALYZE " + node.getRelationName() + ";"
                            : "ANALYZE;")
                    .impactScore(node.getTotalActualTimeMs())
                    .evidence(evidence)
                    .build());
        }

        return findings;
    }

    private List<RuleFinding> checkSelectStar(String sql, ParsedPlan plan) {
        if (!SELECT_STAR.matcher(sql).find()) {
            return List.of();
        }

        return List.of(RuleFinding.builder()
                .ruleCode("SELECT_STAR")
                .recommendationType("QUERY_HYGIENE")
                .severity(Severity.LOW)
                .confidence(0.55)
                .title("SELECT * detected")
                .description("Selecting all columns may increase I/O if the application only needs a subset of columns.")
                .impactScore(plan.getExecutionTimeMs() * 0.1)
                .evidence(List.of(
                        ev("pattern", "SELECT *"),
                        ev("execution_time_ms", String.format("%.2f", plan.getExecutionTimeMs()))
                ))
                .build());
    }

    private List<RuleFinding> checkExpensiveJoins(ParsedPlan plan) {
        List<RuleFinding> findings = new ArrayList<>();

        for (PlanNode node : plan.getFlatNodes()) {
            String type = node.getNodeType();
            if (type == null || !type.toLowerCase(Locale.ROOT).contains("join")) {
                continue;
            }

            if (node.getTotalActualTimeMs() < 50) {
                continue;
            }

            boolean hasSeqScanChild = node.getChildren().stream()
                    .anyMatch(c -> "Seq Scan".equalsIgnoreCase(c.getNodeType()));

            if (!hasSeqScanChild) {
                continue;
            }

            findings.add(RuleFinding.builder()
                    .ruleCode("EXPENSIVE_JOIN")
                    .recommendationType("JOIN_OPTIMIZATION")
                    .severity(Severity.MEDIUM)
                    .confidence(0.7)
                    .title("Potentially expensive join strategy")
                    .description("Join node '" + type + "' spent "
                            + String.format("%.1f", node.getTotalActualTimeMs())
                            + "ms with sequential scan inputs.")
                    .impactScore(node.getTotalActualTimeMs())
                    .evidence(List.of(
                            ev("join_type", type),
                            ev("actual_time_ms", String.format("%.2f", node.getTotalActualTimeMs())),
                            ev("actual_rows", String.valueOf(node.getTotalActualRows()))
                    ))
                    .build());
        }

        return findings;
    }

    private List<String> extractFilterColumns(String filter) {
        if (filter == null || filter.isBlank()) {
            return List.of();
        }
        Set<String> columns = new LinkedHashSet<>();
        Matcher matcher = WHERE_COLUMN.matcher(filter);
        while (matcher.find()) {
            columns.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(columns);
    }

    private List<String> extractWhereColumns(String sql, String table) {
        String lower = sql.toLowerCase(Locale.ROOT);
        int whereIdx = lower.indexOf(" where ");
        if (whereIdx < 0) {
            return List.of();
        }
        String whereClause = lower.substring(whereIdx);
        if (table != null && !whereClause.contains(table.toLowerCase(Locale.ROOT))) {
            // still extract generic columns from WHERE
        }
        Set<String> columns = new LinkedHashSet<>();
        Matcher matcher = WHERE_COLUMN.matcher(whereClause);
        while (matcher.find()) {
            columns.add(matcher.group(1));
        }
        return new ArrayList<>(columns);
    }

    private boolean indexCoversColumns(String indexDef, List<String> columns) {
        if (columns.isEmpty()) {
            return false;
        }
        String def = indexDef.toLowerCase(Locale.ROOT);
        return columns.stream().allMatch(def::contains);
    }

    private String buildIndexSuggestion(String table, List<String> columns) {
        if (columns.isEmpty()) {
            return "-- Unable to infer index columns from plan filter";
        }
        String cols = String.join(", ", columns);
        return "CREATE INDEX idx_" + table + "_" + String.join("_", columns)
                + " ON " + table + "(" + cols + ");";
    }

    private String formatIndexes(List<IndexInfo> indexes) {
        if (indexes.isEmpty()) {
            return "none";
        }
        return indexes.stream().map(IndexInfo::getIndexName).reduce((a, b) -> a + ", " + b).orElse("none");
    }

    private EvidenceItem ev(String key, String value) {
        return new EvidenceItem(key, value);
    }

    private String formatNumber(long n) {
        if (n >= 1_000_000) {
            return String.format("%.1fM", n / 1_000_000.0);
        }
        if (n >= 1_000) {
            return String.format("%.1fK", n / 1_000.0);
        }
        return String.valueOf(n);
    }
}
