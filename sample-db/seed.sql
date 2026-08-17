-- Seed realistic volume so sequential scans are painful (kept moderate for faster first boot)
INSERT INTO customers (name, email, country, created_at)
SELECT
    'Customer ' || g,
    'customer' || g || '@example.com',
    (ARRAY['US', 'IN', 'UK', 'DE', 'FR', 'CA', 'AU', 'JP'])[1 + (g % 8)],
    NOW() - (g || ' days')::INTERVAL
FROM generate_series(1, 20000) g;

INSERT INTO products (name, category, price, created_at)
SELECT
    'Product ' || g,
    (ARRAY['electronics', 'books', 'clothing', 'home', 'sports'])[1 + (g % 5)],
    (round((random() * 500 + 5)::numeric, 2)),
    NOW() - (g || ' hours')::INTERVAL
FROM generate_series(1, 2000) g;

INSERT INTO orders (customer_id, product_id, quantity, total_amount, status, created_at)
SELECT
    1 + (g % 20000),
    1 + (g % 2000),
    1 + (g % 5),
    round((random() * 1000 + 10)::numeric, 2),
    (ARRAY['pending', 'shipped', 'delivered', 'cancelled'])[1 + (g % 4)],
    NOW() - (g || ' minutes')::INTERVAL
FROM generate_series(1, 150000) g;

ANALYZE customers;
ANALYZE products;
ANALYZE orders;
