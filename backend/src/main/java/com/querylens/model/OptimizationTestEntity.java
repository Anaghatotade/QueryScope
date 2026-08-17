package com.querylens.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "optimization_tests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptimizationTestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recommendation_id", nullable = false)
    private UUID recommendationId;

    @Column(name = "baseline_time_ms")
    private Double baselineTimeMs;

    @Column(name = "optimized_time_ms")
    private Double optimizedTimeMs;

    @Column(name = "baseline_rows_scanned")
    private Long baselineRowsScanned;

    @Column(name = "optimized_rows_scanned")
    private Long optimizedRowsScanned;

    @Column(name = "improvement_percent")
    private Double improvementPercent;

    @Column(name = "test_status", nullable = false, length = 32)
    private String testStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "test_details", columnDefinition = "jsonb")
    private String testDetails;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
