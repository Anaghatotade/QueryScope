-- Sample e-commerce schema with intentional performance anti-patterns

CREATE TABLE customers (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    email       TEXT NOT NULL,
    country     CHAR(2) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    category    TEXT NOT NULL,
    price       NUMERIC(10, 2) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE orders (
    id           BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT NOT NULL REFERENCES customers(id),
    product_id   BIGINT NOT NULL REFERENCES products(id),
    quantity     INT NOT NULL DEFAULT 1,
    total_amount NUMERIC(12, 2) NOT NULL,
    status       TEXT NOT NULL DEFAULT 'pending',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Intentionally suboptimal indexes (missing composite on customer_id + created_at)
CREATE INDEX orders_status_idx ON orders(status);
CREATE INDEX customers_country_idx ON customers(country);

ANALYZE customers;
ANALYZE products;
ANALYZE orders;
