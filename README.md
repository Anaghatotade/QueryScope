# QueryLens

**Automatic SQL Query Performance Analyzer & Optimizer**

QueryLens captures SQL workloads, analyzes PostgreSQL execution plans, identifies performance bottlenecks, and generates evidence-backed optimization recommendations — with optional verification experiments.

> **Core question:** A developer knows their database is slow. Can QueryLens automatically explain why, prove what's causing it, and recommend the safest optimization?

---

## Features

- **Read-only SQL analysis** — safe `SELECT`/`WITH` validation before any execution
- **Query normalization & fingerprinting** — group query patterns by structure
- **Execution plan parsing** — recursive plan tree from `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`
- **Rule-based diagnosis engine** — deterministic, testable rules (no LLM guesswork)
- **Evidence-backed recommendations** — every suggestion includes proof
- **Performance scoring** — transparent multi-dimensional score breakdown
- **Verification experiments** — test index candidates in a rolled-back transaction
- **Dashboard UI** — analyze queries, view health overview, query leaderboard

### Detection rules (v0.1)

| Rule | Description |
|------|-------------|
| `SEQ_SCAN_HIGH_SELECTIVITY` | Sequential scan on large table with high scan amplification |
| `CARDINALITY_ESTIMATION` | Large gap between estimated vs actual rows |
| `SELECT_STAR` | Query hygiene — low severity |
| `EXPENSIVE_JOIN` | Join nodes with sequential scan inputs |

---

## Architecture

```text
Developer → REST API → Analysis Orchestrator
                          ├── SQL Safety Guard
                          ├── Query Normalizer / Fingerprinter
                          ├── EXPLAIN Analyzer
                          ├── Metadata Service (indexes, table stats)
                          ├── Rule Engine
                          ├── Performance Scorer
                          └── Verification Service (BEGIN → CREATE INDEX → EXPLAIN → ROLLBACK)

Results → QueryLens PostgreSQL (metadata) + Dashboard UI
Target  → Sample shop PostgreSQL (500K orders, intentional missing indexes)
```

---

## Quick Start (recommended — no Docker/Java needed)

```bash
cd /Users/ankitkumar/QueryLens
./scripts/run_local.sh
```

Then open **http://localhost:3000** and click **Run Analysis**.

This starts a local Python server with:
- Built-in SQLite demo database (80K orders, intentional missing indexes)
- Same dashboard UI
- Same `/api/analyze` flow, recommendations, and verify experiments

---

## Full stack with Docker (PostgreSQL + Spring Boot)

**Requires Docker Desktop to be running.**

```bash
./scripts/start.sh
```

| Service | URL |
|---------|-----|
| Dashboard | http://localhost:3000 |
| API | http://localhost:8080 |
| Target DB (shop) | localhost:5433 |
| QueryLens metadata DB | localhost:5434 |

If you see `failed to connect to docker API` → open **Docker Desktop**, wait until it says **Running**, then retry.

---

## Quick Start (Docker alternative)

```bash
docker compose up --build
```

### Try a sample analysis

```bash
curl -X POST http://localhost:8080/api/analyze \
  -H 'Content-Type: application/json' \
  -d '{"sql":"SELECT * FROM orders WHERE customer_id = 123 AND created_at > '\''2026-01-01'\''"}'
```

Or open the dashboard and click **Run Analysis**.

### Verify an index recommendation

After analysis, click **Verify optimization** in the UI, or:

```bash
curl -X POST http://localhost:8080/api/recommendations/{id}/verify
```

---

## Local development (without Docker for backend)

1. Start databases:

```bash
docker compose up target-db querylens-db -d
```

2. Run backend:

```bash
cd backend
mvn spring-boot:run
```

3. Serve frontend:

```bash
cd frontend
python3 -m http.server 3000
```

Open http://localhost:3000 (API at http://localhost:8080).

---

## Sample slow queries

```sql
-- Missing composite index on (customer_id, created_at)
SELECT * FROM orders
WHERE customer_id = 123
AND created_at > '2026-01-01';

-- Expensive join with sequential scans
SELECT o.*, c.name, p.name
FROM orders o
JOIN customers c ON o.customer_id = c.id
JOIN products p ON o.product_id = p.id
WHERE c.country = 'IN'
AND o.created_at > '2025-01-01';
```

---

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/analyze` | Analyze a SQL query |
| `GET` | `/api/dashboard/overview` | Health overview stats |
| `GET` | `/api/dashboard/queries` | Query pattern leaderboard |
| `GET` | `/api/dashboard/jobs` | Recent analysis jobs |
| `POST` | `/api/recommendations/{id}/verify` | Run optimization experiment |

---

## Project structure

```text
QueryLens/
├── backend/           # Spring Boot API + analysis engine
├── frontend/          # Dashboard UI (static + nginx)
├── sample-db/         # Target database schema + seed data
└── docker-compose.yml
```

---

## Security model

- Only `SELECT` / `WITH` queries accepted for analysis
- Multi-statement SQL blocked
- Destructive keywords rejected (`DROP`, `DELETE`, `UPDATE`, etc.)
- Verification uses `BEGIN` → `CREATE INDEX` → `EXPLAIN` → `ROLLBACK` (never modifies production permanently)

---

## Roadmap

- [ ] `pg_stat_statements` workload ingestion
- [ ] N+1 query pattern detection
- [ ] Plan change regression detection
- [ ] Incident mode (latency spike alerts)
- [ ] Optional LLM narrative layer on top of deterministic evidence

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `docker.sock: no such file` | Open **Docker Desktop** and wait until running |
| `docker compose up` hangs on pull | Check internet; or use `./scripts/run_local.sh` instead |
| `Unable to locate a Java Runtime` | Use `./scripts/run_local.sh` (no Java needed) |
| Dashboard loads but analyze fails | Ensure server is running on port 3000 |
| Port 3000 already in use | `lsof -ti:3000 \| xargs kill -9` then restart |

---

## License

MIT
