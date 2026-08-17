package com.querylens.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "query_fingerprints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryFingerprint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "fingerprint_hash", nullable = false, unique = true, length = 64)
    private String fingerprintHash;

    @Column(name = "normalized_sql", nullable = false, columnDefinition = "TEXT")
    private String normalizedSql;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Column(name = "execution_count", nullable = false)
    private long executionCount;

    @Column(name = "total_execution_ms", nullable = false)
    private double totalExecutionMs;

    @Column(name = "avg_execution_ms", nullable = false)
    private double avgExecutionMs;
}
