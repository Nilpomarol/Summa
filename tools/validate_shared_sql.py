#!/usr/bin/env python3
"""Validate shared SQLite schema, migration, and canonical views."""

from __future__ import annotations

import sqlite3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
QUERY_ORDER = [
    "v_movement_shared.sql",
    "v_account_flow.sql",
    "v_account_balance.sql",
    "v_actual_expense.sql",
    "v_actual_income.sql",
    "v_person_balance.sql",
]
VIEW_NAMES = [path.removesuffix(".sql") for path in QUERY_ORDER]


def fail(message: str) -> None:
    print(f"shared SQL validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def read_sql(path: Path) -> str:
    if not path.exists():
        fail(f"missing SQL file: {path}")
    return path.read_text(encoding="utf-8")


def apply_views(conn: sqlite3.Connection) -> None:
    for name in QUERY_ORDER:
        conn.executescript(read_sql(ROOT / "shared" / "queries" / name))
    for view in VIEW_NAMES:
        conn.execute(f"SELECT * FROM {view} LIMIT 0").fetchall()


def validate_entrypoint(label: str, path: Path, expect_seeded_meta: bool) -> None:
    conn = sqlite3.connect(":memory:")
    try:
        conn.executescript(read_sql(path))
        apply_views(conn)
        meta = dict(conn.execute("SELECT key, value FROM meta").fetchall())
    except sqlite3.Error as exc:
        fail(f"{label}: {exc}")
    finally:
        conn.close()

    if expect_seeded_meta:
        expected = {"schema_version": "1", "snapshot_version": "0"}
        if meta != expected:
            fail(f"{label}: expected meta seed {expected}, got {meta}")
    elif meta:
        fail(f"{label}: schema.sql should not seed meta, got {meta}")


def main() -> None:
    validate_entrypoint("schema", ROOT / "shared" / "schema" / "schema.sql", False)
    validate_entrypoint("migration", ROOT / "shared" / "migrations" / "001_initial.sql", True)
    print("validated shared SQL schema, migration, and views")


if __name__ == "__main__":
    main()
