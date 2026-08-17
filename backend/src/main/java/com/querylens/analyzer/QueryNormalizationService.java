package com.querylens.analyzer;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

@Service
public class QueryNormalizationService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:''|[^'])*'");
    private static final Pattern NUMBER_LITERAL = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");
    private static final Pattern IN_LIST = Pattern.compile("\\bIN\\s*\\([^)]*\\)", Pattern.CASE_INSENSITIVE);

    public String normalize(String sql) {
        String cleaned = sql.trim();
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        cleaned = WHITESPACE.matcher(cleaned).replaceAll(" ");
        cleaned = STRING_LITERAL.matcher(cleaned).replaceAll("?");
        cleaned = IN_LIST.matcher(cleaned).replaceAll("IN (?)");
        cleaned = NUMBER_LITERAL.matcher(cleaned).replaceAll("?");
        return cleaned;
    }

    public String fingerprint(String normalizedSql) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedSql.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
