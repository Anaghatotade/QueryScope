package com.querylens.analyzer;

import lombok.Builder;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DatabaseMetadataService {

    private final JdbcTemplate targetJdbc;

    public DatabaseMetadataService(JdbcTemplate targetJdbc) {
        this.targetJdbc = targetJdbc;
    }

    public long getTableRowEstimate(String tableName) {
        if (tableName == null) {
            return 0;
        }
        Long estimate = targetJdbc.queryForObject(
                """
                SELECT COALESCE(c.reltuples, 0)::bigint
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE c.relname = ? AND n.nspname = 'public'
                """,
                Long.class,
                tableName.toLowerCase(Locale.ROOT)
        );
        return estimate != null ? estimate : 0;
    }

    public List<IndexInfo> getIndexesForTable(String tableName) {
        if (tableName == null) {
            return List.of();
        }
        return targetJdbc.query(
                """
                SELECT indexname, indexdef
                FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = ?
                ORDER BY indexname
                """,
                (rs, rowNum) -> IndexInfo.builder()
                        .indexName(rs.getString("indexname"))
                        .indexDefinition(rs.getString("indexdef"))
                        .build(),
                tableName.toLowerCase(Locale.ROOT)
        );
    }

    public List<IndexInfo> getAllIndexes() {
        return targetJdbc.query(
                """
                SELECT tablename, indexname, indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                ORDER BY tablename, indexname
                """,
                (rs, rowNum) -> IndexInfo.builder()
                        .tableName(rs.getString("tablename"))
                        .indexName(rs.getString("indexname"))
                        .indexDefinition(rs.getString("indexdef"))
                        .build()
        );
    }

    public DatabaseStats getDatabaseStats() {
        Long tables = targetJdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'public'", Long.class);
        Long totalRows = targetJdbc.queryForObject(
                """
                SELECT COALESCE(SUM(c.reltuples), 0)::bigint
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind = 'r'
                """,
                Long.class
        );
        return DatabaseStats.builder()
                .tableCount(tables != null ? tables : 0)
                .estimatedTotalRows(totalRows != null ? totalRows : 0)
                .build();
    }

    @Data
    @Builder
    public static class IndexInfo {
        private String tableName;
        private String indexName;
        private String indexDefinition;
    }

    @Data
    @Builder
    public static class DatabaseStats {
        private long tableCount;
        private long estimatedTotalRows;
    }
}
