package com.querylens.repository;

import com.querylens.model.QueryFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueryFingerprintRepository extends JpaRepository<QueryFingerprint, UUID> {
    Optional<QueryFingerprint> findByFingerprintHash(String fingerprintHash);
    List<QueryFingerprint> findTop20ByOrderByTotalExecutionMsDesc();
}
