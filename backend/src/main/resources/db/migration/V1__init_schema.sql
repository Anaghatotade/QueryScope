-- QueryLens metadata schema
CREATE TABLE query_fingerprints (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fingerprint_hash    VARCHAR(64) NOT NULL UNIQUE,
    normalized_sql      TEXT NOT NULL,
    first_seen          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    execution_count     BIGINT NOT NULL DEFAULT 0,
    total_execution_ms  DOUBLE PRECISION NOT NULL DEFAULT 0,
    avg_execution_ms    DOUBLE PRECISION NOT NULL DEFAULT 0
);

CREATE TABLE analysis_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fingerprint_id  UUID REFERENCES query_fingerprints(id),
    original_sql    TEXT NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

CREATE TABLE query_executions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id              UUID NOT NULL REFERENCES analysis_jobs(id) ON DELETE CASCADE,
    fingerprint_id      UUID REFERENCES query_fingerprints(id),
    execution_time_ms   DOUBLE PRECISION,
    planning_time_ms    DOUBLE PRECISION,
    rows_returned       BIGINT,
    captured_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE execution_plans (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    execution_id        UUID NOT NULL REFERENCES query_executions(id) ON DELETE CASCADE,
    plan_json           JSONB NOT NULL,
    root_node_type      VARCHAR(64),
    total_cost          DOUBLE PRECISION,
    captured_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE plan_nodes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id             UUID NOT NULL REFERENCES execution_plans(id) ON DELETE CASCADE,
    parent_id           UUID REFERENCES plan_nodes(id) ON DELETE CASCADE,
    node_type           VARCHAR(64) NOT NULL,
    relation_name       VARCHAR(255),
    estimated_rows      DOUBLE PRECISION,
    actual_rows         BIGINT,
    startup_cost        DOUBLE PRECISION,
    total_cost          DOUBLE PRECISION,
    actual_time_ms      DOUBLE PRECISION,
    loops               INT DEFAULT 1,
    shared_hit_blocks   BIGINT DEFAULT 0,
    shared_read_blocks  BIGINT DEFAULT 0,
    filter              TEXT,
    index_name          VARCHAR(255),
    sort_key            INT NOT NULL DEFAULT 0
);

CREATE TABLE recommendations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id              UUID NOT NULL REFERENCES analysis_jobs(id) ON DELETE CASCADE,
    rule_code           VARCHAR(64) NOT NULL,
    recommendation_type VARCHAR(64) NOT NULL,
    severity            VARCHAR(16) NOT NULL,
    confidence          DOUBLE PRECISION NOT NULL,
    title               TEXT NOT NULL,
    description         TEXT NOT NULL,
    suggested_sql       TEXT,
    impact_score        DOUBLE PRECISION NOT NULL DEFAULT 0,
    verified            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE recommendation_evidence (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id   UUID NOT NULL REFERENCES recommendations(id) ON DELETE CASCADE,
    evidence_key        VARCHAR(128) NOT NULL,
    evidence_value      TEXT NOT NULL,
    sort_order          INT NOT NULL DEFAULT 0
);

CREATE TABLE optimization_tests (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id       UUID NOT NULL REFERENCES recommendations(id) ON DELETE CASCADE,
    baseline_time_ms        DOUBLE PRECISION,
    optimized_time_ms       DOUBLE PRECISION,
    baseline_rows_scanned   BIGINT,
    optimized_rows_scanned  BIGINT,
    improvement_percent     DOUBLE PRECISION,
    test_status             VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    test_details            JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fingerprints_hash ON query_fingerprints(fingerprint_hash);
CREATE INDEX idx_jobs_status ON analysis_jobs(status);
CREATE INDEX idx_recommendations_job ON recommendations(job_id);
CREATE INDEX idx_plan_nodes_plan ON plan_nodes(plan_id);
