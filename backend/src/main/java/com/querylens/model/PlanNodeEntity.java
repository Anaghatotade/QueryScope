package com.querylens.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plan_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanNodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "node_type", nullable = false, length = 64)
    private String nodeType;

    @Column(name = "relation_name")
    private String relationName;

    @Column(name = "estimated_rows")
    private Double estimatedRows;

    @Column(name = "actual_rows")
    private Long actualRows;

    @Column(name = "startup_cost")
    private Double startupCost;

    @Column(name = "total_cost")
    private Double totalCost;

    @Column(name = "actual_time_ms")
    private Double actualTimeMs;

    private Integer loops;

    @Column(name = "shared_hit_blocks")
    private Long sharedHitBlocks;

    @Column(name = "shared_read_blocks")
    private Long sharedReadBlocks;

    @Column(columnDefinition = "TEXT")
    private String filter;

    @Column(name = "index_name")
    private String indexName;

    @Column(name = "sort_key", nullable = false)
    private int sortKey;
}
