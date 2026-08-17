-- Sample queries for QueryLens demos

-- 1. Missing composite index (high scan amplification)
SELECT *
FROM orders
WHERE customer_id = 123
AND created_at > '2026-01-01';

-- 2. Expensive join with filter on joined table
SELECT o.*, c.name, p.name AS product_name
FROM orders o
JOIN customers c ON o.customer_id = c.id
JOIN products p ON o.product_id = p.id
WHERE c.country = 'IN'
AND o.created_at > '2025-01-01';

-- 3. SELECT * hygiene (usually low severity)
SELECT * FROM customers WHERE id = 10;

-- 4. High frequency pattern (run multiple times to build fingerprint stats)
SELECT * FROM orders WHERE customer_id = 999;
