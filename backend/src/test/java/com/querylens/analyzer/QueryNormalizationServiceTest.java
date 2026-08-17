package com.querylens.analyzer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueryNormalizationServiceTest {

    private final QueryNormalizationService service = new QueryNormalizationService();

    @Test
    void normalizesLiteralsToPlaceholders() {
        String a = "SELECT * FROM users WHERE id = 123";
        String b = "SELECT * FROM users WHERE id = 456";
        assertEquals(service.normalize(a), service.normalize(b));
    }

    @Test
    void generatesStableFingerprint() {
        String normalized = "SELECT * FROM orders WHERE customer_id = ?";
        assertEquals(service.fingerprint(normalized), service.fingerprint(normalized));
    }
}
