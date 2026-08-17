package com.querylens.analyzer;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SqlSafetyService {

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE",
            "CREATE", "GRANT", "REVOKE", "COPY", "CALL", "DO", "VACUUM"
    );

    private static final Pattern FIRST_KEYWORD = Pattern.compile("^\\s*(\\w+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTI_STATEMENT = Pattern.compile(";");
    private static final Pattern COMMENT_BLOCK = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    public void validateReadOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL query must not be empty");
        }

        String cleaned = COMMENT_BLOCK.matcher(sql).replaceAll(" ").trim();
        if (MULTI_STATEMENT.matcher(cleaned).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multiple SQL statements are not allowed");
        }

        var matcher = FIRST_KEYWORD.matcher(cleaned);
        if (!matcher.find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to parse SQL statement");
        }

        String keyword = matcher.group(1).toUpperCase(Locale.ROOT);
        if (FORBIDDEN_KEYWORDS.contains(keyword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only read-only SELECT/WITH queries are supported. Found: " + keyword);
        }

        if (!keyword.equals("SELECT") && !keyword.equals("WITH") && !keyword.equals("EXPLAIN")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only SELECT/WITH queries are supported for analysis");
        }
    }
}
