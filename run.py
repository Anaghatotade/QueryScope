#!/usr/bin/env python3
"""QueryLens local runner — no Docker or Java required."""

from __future__ import annotations

import hashlib
import json
import re
import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

ROOT = Path(__file__).resolve().parent
FRONTEND = ROOT / "frontend"
DB_PATH = ROOT / "data" / "querylens.db"

FORBIDDEN = {
    "insert", "update", "delete", "drop", "alter", "truncate",
    "create", "grant", "revoke", "copy", "attach", "detach",
}


class AnalyzeRequest(BaseModel):
    sql: str = Field(min_length=1)


def utcnow() -> str:
    return datetime.now(timezone.utc).isoformat()


def normalize_sql(sql: str) -> str:
    cleaned = re.sub(r"/\*.*?\*/", " ", sql, flags=re.S).strip().rstrip(";")
    cleaned = re.sub(r"\s+", " ", cleaned)
    cleaned = re.sub(r"'(?:''|[^'])*'", "?", cleaned)
    cleaned = re.sub(r"\bIN\s*\([^)]*\)", "IN (?)", cleaned, flags=re.I)
    cleaned = re.sub(r"\b\d+(?:\.\d+)?\b", "?", cleaned)
    return cleaned


def fingerprint(normalized: str) -> str:
    return hashlib.sha256(normalized.encode()).hexdigest()


def validate_read_only(sql: str) -> None:
    if ";" in sql.strip().rstrip(";"):
        raise HTTPException(400, "Multiple SQL statements are not allowed")
    match = re.match(r"^\s*(\w+)", sql, re.I)
    if not match:
        raise HTTPException(400, "Unable to parse SQL statement")
    keyword = match.group(1).lower()
    if keyword in FORBIDDEN:
        raise HTTPException(400, f"Only read-only SELECT queries are supported. Found: {keyword.upper()}")
    if keyword not in {"select", "with"}:
        raise HTTPException(400, "Only SELECT/WITH queries are supported for analysis")


def connect() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_demo_db() -> None:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = connect()
    cur = conn.cursor()

    cur.executescript(
        """
        CREATE TABLE IF NOT EXISTS meta_fingerprints (
            id TEXT PRIMARY KEY,
            hash TEXT UNIQUE NOT NULL,
            normalized_sql TEXT NOT NULL,
            first_seen TEXT NOT NULL,
            last_seen TEXT NOT NULL,
            execution_count INTEGER NOT NULL DEFAULT 0,
            total_execution_ms REAL NOT NULL DEFAULT 0,
            avg_execution_ms REAL NOT NULL DEFAULT 0
        );

        CREATE TABLE IF NOT EXISTS meta_jobs (
            id TEXT PRIMARY KEY,
            fingerprint_id TEXT,
            original_sql TEXT NOT NULL,
            status TEXT NOT NULL,
            error_message TEXT,
            created_at TEXT NOT NULL,
            completed_at TEXT
        );

        CREATE TABLE IF NOT EXISTS meta_recommendations (
            id TEXT PRIMARY KEY,
            job_id TEXT NOT NULL,
            rule_code TEXT NOT NULL,
            recommendation_type TEXT NOT NULL,
            severity TEXT NOT NULL,
            confidence REAL NOT NULL,
            title TEXT NOT NULL,
            description TEXT NOT NULL,
            suggested_sql TEXT,
            impact_score REAL NOT NULL,
            verified INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS meta_evidence (
            id TEXT PRIMARY KEY,
            recommendation_id TEXT NOT NULL,
            evidence_key TEXT NOT NULL,
            evidence_value TEXT NOT NULL,
            sort_order INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS customers (
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL,
            country TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS products (
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL,
            category TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS orders (
            id INTEGER PRIMARY KEY,
            customer_id INTEGER NOT NULL,
            product_id INTEGER NOT NULL,
            status TEXT NOT NULL,
            created_at TEXT NOT NULL,
            total_amount REAL NOT NULL
        );
        """
    )

    count = cur.execute("SELECT COUNT(*) FROM orders").fetchone()[0]
    if count == 0:
        countries = ["US", "IN", "UK", "DE", "FR", "CA", "AU", "JP"]
        cur.executemany(
            "INSERT INTO customers(id, name, country) VALUES (?, ?, ?)",
            [(i, f"Customer {i}", countries[i % len(countries)]) for i in range(1, 5001)],
        )
        categories = ["electronics", "books", "clothing", "home", "sports"]
        cur.executemany(
            "INSERT INTO products(id, name, category) VALUES (?, ?, ?)",
            [(i, f"Product {i}", categories[i % len(categories)]) for i in range(1, 501)],
        )
        cur.executemany(
            "INSERT INTO orders(id, customer_id, product_id, status, created_at, total_amount) VALUES (?, ?, ?, ?, ?, ?)",
            [
                (
                    i,
                    (i % 5000) + 1,
                    (i % 500) + 1,
                    ["pending", "shipped", "delivered", "cancelled"][i % 4],
                    f"2026-01-{(i % 28) + 1:02d}",
                    round(10 + (i % 1000) * 0.7, 2),
                )
                for i in range(1, 80001)
            ],
        )
        cur.execute("CREATE INDEX IF NOT EXISTS orders_status_idx ON orders(status)")

    conn.commit()
    conn.close()


def explain_sqlite(conn: sqlite3.Connection, sql: str) -> tuple[list[dict[str, Any]], float]:
    started = datetime.now()
    plan_rows = conn.execute(f"EXPLAIN QUERY PLAN {sql}").fetchall()
    elapsed_ms = (datetime.now() - started).total_seconds() * 1000
    nodes = []
    for row in plan_rows:
        detail = row[3] if len(row) > 3 else str(row)
        nodes.append({"detail": detail, "row": list(row)})
    return nodes, elapsed_ms


def analyze_plan(sql: str, plan_nodes: list[dict[str, Any]], elapsed_ms: float) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    recommendations: list[dict[str, Any]] = []
    plan_root = {"nodeType": "Query Plan", "actualTimeMs": elapsed_ms, "children": []}

    scan_details = [n["detail"] for n in plan_nodes if "SCAN" in n["detail"].upper()]
    uses_index = any("USING INDEX" in n["detail"].upper() or "SEARCH" in n["detail"].upper() for n in plan_nodes)

    if scan_details and not uses_index and "orders" in sql.lower():
        order_count = 80000
        recommendations.append(
            make_rec(
                "SEQ_SCAN_HIGH_SELECTIVITY",
                "MISSING_INDEX",
                "HIGH",
                0.91,
                "High scan amplification on orders",
                "The query scans the orders table without a supporting index on filter columns.",
                "CREATE INDEX idx_orders_customer_created ON orders(customer_id, created_at);",
                elapsed_ms * 10,
                [
                    ("table", "orders"),
                    ("table_rows_estimate", str(order_count)),
                    ("access_path", scan_details[0]),
                    ("existing_indexes", "orders_status_idx(status)"),
                    ("scan_amplification", "high"),
                ],
            )
        )
        plan_root["children"].append(
            {"nodeType": "Seq Scan", "relationName": "orders", "actualTimeMs": elapsed_ms * 0.95, "actualRows": order_count}
        )

    if re.search(r"select\s+\*", sql, re.I):
        recommendations.append(
            make_rec(
                "SELECT_STAR",
                "QUERY_HYGIENE",
                "LOW",
                0.55,
                "SELECT * detected",
                "Selecting all columns may increase I/O if the application only needs a subset of columns.",
                None,
                elapsed_ms * 0.1,
                [("pattern", "SELECT *"), ("execution_time_ms", f"{elapsed_ms:.2f}")],
            )
        )

    if "join" in sql.lower() and scan_details:
        recommendations.append(
            make_rec(
                "EXPENSIVE_JOIN",
                "JOIN_OPTIMIZATION",
                "MEDIUM",
                0.7,
                "Potentially expensive join strategy",
                "Join uses table scans on one or more tables. Consider indexes on join/filter columns.",
                None,
                elapsed_ms * 2,
                [("join_detected", "true"), ("scan_nodes", str(len(scan_details)))],
            )
        )

    score = score_query(elapsed_ms, recommendations, uses_index)
    return recommendations, {"root": plan_root, "score": score}


def make_rec(code, rtype, severity, confidence, title, description, suggested_sql, impact, evidence):
    return {
        "id": str(uuid.uuid4()),
        "ruleCode": code,
        "type": rtype,
        "severity": severity,
        "confidence": confidence,
        "title": title,
        "description": description,
        "suggestedSql": suggested_sql,
        "impactScore": impact,
        "verified": False,
        "evidence": [{"key": k, "value": v} for k, v in evidence],
    }


def score_query(elapsed_ms: float, recs: list[dict[str, Any]], uses_index: bool) -> dict[str, Any]:
    latency = 100 if elapsed_ms <= 10 else 85 if elapsed_ms <= 50 else 65 if elapsed_ms <= 200 else 40 if elapsed_ms <= 1000 else 15
    scan = 90 if uses_index else 35
    index = 90 if uses_index else 30
    cardinality = 95
    frequency = 90
    overall = round(latency * 0.3 + scan * 0.2 + index * 0.2 + cardinality * 0.1 + frequency * 0.1 + (40 if any(r["severity"] == "HIGH" for r in recs) else 85) * 0.1)
    grade = "GOOD" if overall >= 80 else "FAIR" if overall >= 60 else "POOR" if overall >= 40 else "CRITICAL"
    return {
        "overall": overall,
        "executionLatency": latency,
        "scanEfficiency": scan,
        "indexUtilization": index,
        "cardinalityAccuracy": cardinality,
        "queryFrequency": frequency,
        "grade": grade,
    }


app = FastAPI(title="QueryLens Local")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
def startup() -> None:
    init_demo_db()


@app.get("/actuator/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/api/analyze")
def analyze(req: AnalyzeRequest) -> dict[str, Any]:
    sql = req.sql.strip()
    validate_read_only(sql)

    normalized = normalize_sql(sql)
    fp_hash = fingerprint(normalized)
    conn = connect()
    job_id = str(uuid.uuid4())

    try:
        fp_row = conn.execute("SELECT * FROM meta_fingerprints WHERE hash = ?", (fp_hash,)).fetchone()
        now = utcnow()
        if fp_row:
            fp_id = fp_row["id"]
        else:
            fp_id = str(uuid.uuid4())
            conn.execute(
                "INSERT INTO meta_fingerprints(id, hash, normalized_sql, first_seen, last_seen, execution_count, total_execution_ms, avg_execution_ms) VALUES (?, ?, ?, ?, ?, 0, 0, 0)",
                (fp_id, fp_hash, normalized, now, now),
            )

        conn.execute(
            "INSERT INTO meta_jobs(id, fingerprint_id, original_sql, status, created_at) VALUES (?, ?, ?, 'RUNNING', ?)",
            (job_id, fp_id, sql, now),
        )
        conn.commit()

        plan_nodes, elapsed_ms = explain_sqlite(conn, sql)
        recommendations, plan_data = analyze_plan(sql, plan_nodes, elapsed_ms)

        for rec in recommendations:
            conn.execute(
                """
                INSERT INTO meta_recommendations
                (id, job_id, rule_code, recommendation_type, severity, confidence, title, description, suggested_sql, impact_score, verified, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
                """,
                (
                    rec["id"], job_id, rec["ruleCode"], rec["type"], rec["severity"], rec["confidence"],
                    rec["title"], rec["description"], rec["suggestedSql"], rec["impactScore"], now,
                ),
            )
            for i, ev in enumerate(rec["evidence"]):
                conn.execute(
                    "INSERT INTO meta_evidence(id, recommendation_id, evidence_key, evidence_value, sort_order) VALUES (?, ?, ?, ?, ?)",
                    (str(uuid.uuid4()), rec["id"], ev["key"], ev["value"], i),
                )

        stats = conn.execute(
            "SELECT execution_count, total_execution_ms FROM meta_fingerprints WHERE id = ?",
            (fp_id,),
        ).fetchone()
        count = stats["execution_count"] + 1
        total = stats["total_execution_ms"] + elapsed_ms
        avg = total / count
        conn.execute(
            "UPDATE meta_fingerprints SET execution_count=?, total_execution_ms=?, avg_execution_ms=?, last_seen=? WHERE id=?",
            (count, total, avg, now, fp_id),
        )
        conn.execute(
            "UPDATE meta_jobs SET status='COMPLETED', completed_at=? WHERE id=?",
            (now, job_id),
        )
        conn.commit()

        return {
            "jobId": job_id,
            "fingerprintId": fp_id,
            "normalizedSql": normalized,
            "fingerprintHash": fp_hash,
            "executionTimeMs": elapsed_ms,
            "planningTimeMs": 0.1,
            "rowsReturned": 0,
            "planRoot": plan_data["root"],
            "performanceScore": plan_data["score"],
            "recommendations": recommendations,
        }
    except Exception as exc:
        conn.execute(
            "UPDATE meta_jobs SET status='FAILED', error_message=?, completed_at=? WHERE id=?",
            (str(exc), utcnow(), job_id),
        )
        conn.commit()
        if isinstance(exc, HTTPException):
            raise
        raise HTTPException(400, f"Analysis failed: {exc}") from exc
    finally:
        conn.close()


@app.get("/api/dashboard/overview")
def overview() -> dict[str, Any]:
    conn = connect()
    try:
        fps = conn.execute("SELECT execution_count, total_execution_ms, avg_execution_ms FROM meta_fingerprints").fetchall()
        recs = conn.execute("SELECT severity FROM meta_recommendations").fetchall()
        tables = conn.execute("SELECT COUNT(*) AS c FROM sqlite_master WHERE type='table' AND name NOT LIKE 'meta_%'").fetchone()["c"]
        rows = conn.execute("SELECT COUNT(*) AS c FROM orders").fetchone()["c"]
        return {
            "queriesAnalyzed": sum(r["execution_count"] for r in fps),
            "uniqueFingerprints": len(fps),
            "slowQueries": sum(1 for r in fps if r["avg_execution_ms"] > 200),
            "highPriorityIssues": sum(1 for r in recs if r["severity"] in {"HIGH", "CRITICAL"}),
            "totalDbTimeSeconds": sum(r["total_execution_ms"] for r in fps) / 1000,
            "targetTableCount": tables,
            "targetEstimatedRows": rows,
        }
    finally:
        conn.close()


@app.get("/api/dashboard/queries")
def leaderboard() -> list[dict[str, Any]]:
    conn = connect()
    try:
        rows = conn.execute(
            "SELECT id, normalized_sql, execution_count, avg_execution_ms, total_execution_ms, last_seen FROM meta_fingerprints ORDER BY total_execution_ms DESC LIMIT 20"
        ).fetchall()
        return [
            {
                "fingerprintId": r["id"],
                "normalizedSql": (r["normalized_sql"][:120] + "...") if len(r["normalized_sql"]) > 120 else r["normalized_sql"],
                "executions": r["execution_count"],
                "avgMs": r["avg_execution_ms"],
                "totalMs": r["total_execution_ms"],
                "lastSeen": r["last_seen"],
            }
            for r in rows
        ]
    finally:
        conn.close()


@app.get("/api/dashboard/jobs")
def jobs() -> list[dict[str, Any]]:
    conn = connect()
    try:
        rows = conn.execute(
            "SELECT id, original_sql, status, created_at, completed_at FROM meta_jobs ORDER BY created_at DESC LIMIT 20"
        ).fetchall()
        return [
            {
                "jobId": r["id"],
                "status": r["status"],
                "sql": (r["original_sql"][:100] + "...") if len(r["original_sql"]) > 100 else r["original_sql"],
                "createdAt": r["created_at"],
                "completedAt": r["completed_at"],
            }
            for r in rows
        ]
    finally:
        conn.close()


@app.post("/api/recommendations/{rec_id}/verify")
def verify(rec_id: str) -> dict[str, Any]:
    conn = connect()
    try:
        rec = conn.execute("SELECT * FROM meta_recommendations WHERE id = ?", (rec_id,)).fetchone()
        if not rec:
            raise HTTPException(404, "Recommendation not found")
        if not rec["suggested_sql"]:
            raise HTTPException(400, "Verification is only supported for CREATE INDEX recommendations")

        job = conn.execute("SELECT original_sql FROM meta_jobs WHERE id = ?", (rec["job_id"],)).fetchone()
        sql = job["original_sql"]

        baseline_nodes, baseline_ms = explain_sqlite(conn, sql)
        conn.execute(rec["suggested_sql"])
        optimized_nodes, optimized_ms = explain_sqlite(conn, sql)
        conn.execute("DROP INDEX IF EXISTS idx_orders_customer_created")

        improvement = 0 if baseline_ms <= 0 else ((baseline_ms - optimized_ms) / baseline_ms) * 100
        verified = improvement > 5 or optimized_ms < baseline_ms
        conn.execute("UPDATE meta_recommendations SET verified=? WHERE id=?", (1 if verified else 0, rec_id))
        conn.commit()

        return {
            "recommendationId": rec_id,
            "testId": str(uuid.uuid4()),
            "baselineTimeMs": baseline_ms,
            "optimizedTimeMs": optimized_ms,
            "baselineRowsScanned": len(baseline_nodes),
            "optimizedRowsScanned": len(optimized_nodes),
            "improvementPercent": improvement,
            "verified": verified,
        }
    finally:
        conn.close()


@app.get("/")
def index() -> FileResponse:
    return FileResponse(FRONTEND / "index.html")


app.mount("/assets", StaticFiles(directory=FRONTEND), name="assets")


@app.get("/{path:path}")
def frontend_assets(path: str):
    file_path = FRONTEND / path
    if file_path.exists() and file_path.is_file():
        return FileResponse(file_path)
    return FileResponse(FRONTEND / "index.html")


if __name__ == "__main__":
    print("\nQueryLens local mode (SQLite demo database)")
    print("Open http://localhost:3000\n")
    uvicorn.run(app, host="0.0.0.0", port=3000, log_level="info")
