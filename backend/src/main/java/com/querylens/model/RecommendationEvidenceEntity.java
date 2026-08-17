package com.querylens.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "recommendation_evidence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationEvidenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recommendation_id", nullable = false)
    private UUID recommendationId;

    @Column(name = "evidence_key", nullable = false, length = 128)
    private String evidenceKey;

    @Column(name = "evidence_value", nullable = false, columnDefinition = "TEXT")
    private String evidenceValue;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
