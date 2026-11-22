#!/usr/bin/env python3
"""Validate shared SQLite schema, migration, and canonical views."""

from __future__ import annotations

import sqlite3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
VIEW_FILES = [
    "v_movement_shared.sql",
    "v_movement_summary.sql",
    "v_account_flow.sql",
    "v_account_balance.sql",
    "v_account_value.sql",
    "v_actual_expense.sql",
    "v_actual_income.sql",
    "v_person_balance.sql",
    "v_trip_actual_total.sql",
]
MIGRATION_VIEW_FILES = [
    "v_goal_allocation.sql",
    "v_goal_progress.sql",
    "v_account_allocation.sql",
]
ANALYSIS_QUERY_FILES = [
    "goal_account_allocations.sql",
    "analysis_activity_months.sql",
    "analysis_actual_by_category.sql",
    "analysis_actual_breakdown.sql",
    "analysis_account_flow_over_time.sql",
    "analysis_income_vs_expense.sql",
    "analysis_period_totals.sql",
]


def fail(message: str) -> None:
    print(f"shared SQL validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def read_sql(path: Path) -> str:
    if not path.exists():
        fail(f"missing SQL file: {path}")
    return path.read_text(encoding="utf-8")


def apply_views(conn: sqlite3.Connection, files: list[str] = VIEW_FILES) -> None:
    for name in files:
        conn.executescript(read_sql(ROOT / "shared" / "queries" / name))
    check_views(conn, [name.removesuffix(".sql") for name in files])


def check_views(conn: sqlite3.Connection, views: list[str]) -> None:
    for view in views:
        conn.execute(f"SELECT * FROM {view} LIMIT 0").fetchall()


def integrity_triggers() -> str:
    """The trigger section of migration 016, which a fresh install applies after schema.sql."""
    migration = read_sql(ROOT / "shared" / "migrations" / "016_enforce_shared_account_integrity.sql")
    after_guard = migration.split("DROP TABLE shared_account_integrity_guard;")[-1]
    return after_guard.split("UPDATE meta SET value = '16' WHERE key = 'schema_version';")[0]


def build_fresh_install(conn: sqlite3.Connection) -> None:
    """The database a fresh install builds, in the order Android and the .NET harness build it."""
    conn.executescript(read_sql(ROOT / "shared" / "schema" / "schema.sql"))
    conn.executescript(integrity_triggers())
    apply_views(conn, VIEW_FILES + MIGRATION_VIEW_FILES)


def analysis_query(name: str) -> str:
    return read_sql(ROOT / "shared" / "queries" / name)


def validate_analysis_queries() -> None:
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    try:
        build_fresh_install(conn)
        seed_analysis_fixture(conn)

        activity_months = conn.execute(
            analysis_query("analysis_activity_months.sql"),
        ).fetchall()
        if [row["month"] for row in activity_months] != ["2026-06"]:
            fail(f"analysis_activity_months.sql: unexpected rows {[dict(r) for r in activity_months]}")

        params = {
            "from_date": "2026-06-01",
            "to_date": "2026-07-01",
            "one_time_mode": "include",
            "category_nature": None,
            "bucket": "month",
            "account_id": None,
            "category_id": None,
        }

        category_rows = conn.execute(
            analysis_query("analysis_actual_by_category.sql"),
            params,
        ).fetchall()
        category_totals = {
            row["category_id"]: (row["expense_cents"], row["income_cents"], row["net_cents"])
            for row in category_rows
        }
        if category_totals != {
            "salary": (0, 250_000, 250_000),
            "electronics": (5_000, 0, -5_000),
            "groceries": (1_500, 0, -1_500),
        }:
            fail(f"analysis_actual_by_category.sql: unexpected rows {category_totals}")

        variable_rows = conn.execute(
            analysis_query("analysis_actual_by_category.sql"),
            {**params, "category_nature": "variable"},
        ).fetchall()
        variable_totals = {
            row["category_id"]: (row["expense_cents"], row["income_cents"], row["net_cents"])
            for row in variable_rows
        }
        if variable_totals != {
            "electronics": (5_000, 0, -5_000),
            "groceries": (1_500, 0, -1_500),
        }:
            fail(f"analysis_actual_by_category.sql: unexpected variable rows {variable_totals}")

        one_time_rows = conn.execute(
            analysis_query("analysis_actual_by_category.sql"),
            {**params, "one_time_mode": "only"},
        ).fetchall()
        one_time_totals = {
            row["category_id"]: (row["expense_cents"], row["income_cents"], row["net_cents"])
            for row in one_time_rows
        }
        if one_time_totals != {
            "electronics": (5_000, 0, -5_000),
        }:
            fail(f"analysis_actual_by_category.sql: unexpected one-time rows {one_time_totals}")

        period = conn.execute(
            analysis_query("analysis_period_totals.sql"),
            params,
        ).fetchone()
        expected_period = {
            "net_worth_cents": 258_500,
            "actual_income_cents": 250_000,
            "actual_expense_cents": 6_500,
            "net_actual_cents": 243_500,
            "account_flow_cents": 243_500,
            "savings_rate_basis_points": 9_740,
        }
        actual_period = {key: period[key] for key in expected_period}
        if actual_period != expected_period:
            fail(f"analysis_period_totals.sql: expected {expected_period}, got {actual_period}")

        comparison = conn.execute(
            analysis_query("analysis_income_vs_expense.sql"),
            params,
        ).fetchall()
        if [(row["bucket"], row["income_cents"], row["expense_cents"], row["net_cents"]) for row in comparison] != [
            ("2026-06", 250_000, 6_500, 243_500),
        ]:
            fail("analysis_income_vs_expense.sql: unexpected monthly totals")

        flow = conn.execute(
            analysis_query("analysis_account_flow_over_time.sql"),
            {**params, "bucket": "day"},
        ).fetchall()
        flow_totals = {(row["bucket"], row["account_id"]): row["delta_cents"] for row in flow}
        if flow_totals.get(("2026-06-15", "checking")) != -10_000:
            fail("analysis_account_flow_over_time.sql: missing transfer origin leg")
        if flow_totals.get(("2026-06-15", "savings")) != 10_000:
            fail("analysis_account_flow_over_time.sql: missing transfer destination leg")
        transfer_bucket_totals = {
            row["bucket"]: row["bucket_delta_cents"]
            for row in flow
            if row["bucket"] == "2026-06-15"
        }
        if transfer_bucket_totals != {"2026-06-15": 0}:
            fail("analysis_account_flow_over_time.sql: unexpected transfer bucket total")

        breakdown = conn.execute(
            analysis_query("analysis_actual_breakdown.sql"),
            {**params, "group_trips": 0},
        ).fetchall()
        breakdown_totals = {
            row["category_id"]: (row["row_kind"], row["expense_cents"], row["income_cents"], row["net_cents"])
            for row in breakdown
        }
        if breakdown_totals != {
            "salary": ("category", 0, 250_000, 250_000),
            "electronics": ("category", 5_000, 0, -5_000),
            "groceries": ("category", 1_500, 0, -1_500),
        }:
            fail(f"analysis_actual_breakdown.sql: unexpected rows {breakdown_totals}")

        # Account filter: every actual row lives on 'checking', so 'savings' narrows to nothing.
        savings_rows = conn.execute(
            analysis_query("analysis_actual_by_category.sql"),
            {**params, "account_id": "savings"},
        ).fetchall()
        if savings_rows:
            fail(f"analysis_actual_by_category.sql: account filter leaked {[dict(r) for r in savings_rows]}")

        # Category filter: narrowing to groceries keeps only that net-of-refund total.
        groceries_rows = conn.execute(
            analysis_query("analysis_actual_by_category.sql"),
            {**params, "category_id": "groceries"},
        ).fetchall()
        groceries_totals = {
            row["category_id"]: (row["expense_cents"], row["income_cents"], row["net_cents"])
            for row in groceries_rows
        }
        if groceries_totals != {"groceries": (1_500, 0, -1_500)}:
            fail(f"analysis_actual_by_category.sql: category filter unexpected {groceries_totals}")
    except sqlite3.Error as exc:
        fail(f"analysis query validation: {exc}")
    finally:
        conn.close()


def seed_analysis_fixture(conn: sqlite3.Connection) -> None:
    now = "2026-06-01T00:00:00Z"
    conn.executescript(
        f"""
        INSERT INTO accounts
            (id, name, starting_balance_cents, type, display_order, created_at, updated_at)
        VALUES
            ('checking', 'Checking', 10000, 'bank', 0, '{now}', '{now}'),
            ('savings', 'Savings', 5000, 'savings', 1, '{now}', '{now}');

        INSERT INTO categories
            (id, name, kind, nature, display_order, created_at, updated_at)
        VALUES
            ('salary', 'Salary', 'income', 'fixed', 0, '{now}', '{now}'),
            ('groceries', 'Groceries', 'expense', 'variable', 1, '{now}', '{now}'),
            ('electronics', 'Electronics', 'expense', 'variable', 2, '{now}', '{now}');

        INSERT INTO movements
            (id, type, amount_cents, date, account_id, dest_account_id, name, is_one_time, category_id,
             refunds_expense_id, created_at, updated_at)
        VALUES
            ('salary-june', 'income', 250000, '2026-06-01', 'checking', NULL, 'Salary', 0, 'salary',
             NULL, '{now}', '{now}'),
            ('groceries-1', 'expense', 2000, '2026-06-05', 'checking', NULL, 'Groceries', 0, 'groceries',
             NULL, '{now}', '{now}'),
            ('laptop', 'expense', 5000, '2026-06-10', 'checking', NULL, 'Laptop', 1, 'electronics',
             NULL, '{now}', '{now}'),
            ('grocery-refund', 'refund', 500, '2026-06-12', 'checking', NULL, 'Refund', 0, 'electronics',
             'groceries-1', '{now}', '{now}'),
            ('to-savings', 'transfer', 10000, '2026-06-15', 'checking', 'savings', 'Savings transfer', 0, NULL,
             NULL, '{now}', '{now}');
        """
    )


# Upgrades are not replayed here. Today's views over the v6 baseline describe a database no
# device ever had, and SQLite rejects the next ALTER TABLE for it. Android's MigrationTest checks
# each migration against the schema version just before it instead.
def main() -> None:
    conn = sqlite3.connect(":memory:")
    try:
        build_fresh_install(conn)
        meta = dict(conn.execute("SELECT key, value FROM meta").fetchall())
    except sqlite3.Error as exc:
        fail(f"fresh install: {exc}")
    finally:
        conn.close()
    if meta:
        fail(f"fresh install: schema.sql should not seed meta, got {meta}")

    conn = sqlite3.connect(":memory:")
    try:
        conn.executescript(read_sql(ROOT / "shared" / "migrations" / "001_initial.sql"))
        meta = dict(conn.execute("SELECT key, value FROM meta").fetchall())
    except sqlite3.Error as exc:
        fail(f"baseline migration: {exc}")
    finally:
        conn.close()
    if meta != {"schema_version": "6", "snapshot_version": "0"}:
        fail(f"baseline migration: expected the v6 meta seed, got {meta}")

    validate_analysis_queries()
    print("validated shared SQL fresh install, baseline migration, views, and analysis queries")


if __name__ == "__main__":
    main()
