package com.querylens.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "query_executions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private AnalysisJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fingerprint_id")
    private QueryFingerprint fingerprint;

    @Column(name = "execution_time_ms")
    private Double executionTimeMs;

    @Column(name = "planning_time_ms")
    private Double planningTimeMs;

    @Column(name = "rows_returned")
    private Long rowsReturned;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;
}
