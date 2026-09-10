#!/usr/bin/env python3
"""Validate that app SQL inputs are wired from shared SQL artifacts."""

from __future__ import annotations

import re
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
    "analysis_actual_breakdown.sql",
    "analysis_actual_by_category.sql",
    "analysis_account_flow_over_time.sql",
    "analysis_income_vs_expense.sql",
    "analysis_period_totals.sql",
]


def fail(message: str) -> None:
    print(f"SQL parity validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    if not path.exists():
        fail(f"missing file: {path}")
    return path.read_text(encoding="utf-8")


def migration_files() -> list[str]:
    return sorted(path.name for path in (ROOT / "shared" / "migrations").glob("*.sql"))


def latest_schema_version() -> str:
    """The version the highest-numbered migration upgrades to, which a fresh install must seed."""
    latest = migration_files()[-1]
    match = re.search(
        r"UPDATE meta SET value = '(\d+)' WHERE key = 'schema_version';",
        read(ROOT / "shared" / "migrations" / latest),
    )
    if not match or int(latest[:3]) != int(match.group(1)):
        fail(f"{latest}: must end by setting schema_version to its own number")
    return match.group(1)


def extract_strings(label: str, text: str, pattern: str) -> list[str]:
    match = re.search(pattern, text, flags=re.DOTALL)
    if not match:
        fail(f"{label}: could not find expected SQL file list")
    return re.findall(r'"([^"]+)"', match.group(1))


def extract_pair_file_names(label: str, text: str, pattern: str) -> list[str]:
    strings = extract_strings(label, text, pattern)
    return strings[0::2]


def validate_shared_inventory() -> None:
    schema = ROOT / "shared" / "schema" / "schema.sql"
    migration = ROOT / "shared" / "migrations" / "001_initial.sql"
    if not schema.exists():
        fail(f"missing shared schema: {schema}")
    if not migration.exists():
        fail(f"missing shared migration: {migration}")

    actual_queries = sorted(path.name for path in (ROOT / "shared" / "queries").glob("*.sql"))
    expected_queries = sorted(VIEW_FILES + MIGRATION_VIEW_FILES + ANALYSIS_QUERY_FILES)
    if actual_queries != expected_queries:
        fail(f"shared query inventory mismatch: expected {expected_queries}, got {actual_queries}")


def validate_android_wiring() -> None:
    build_gradle = read(ROOT / "android" / "app" / "build.gradle.kts")
    android_views = extract_strings(
        "android/app/build.gradle.kts",
        build_gradle,
        r"val\s+sharedViewFiles\s*=\s*listOf\((.*?)\)",
    )
    if android_views != VIEW_FILES + MIGRATION_VIEW_FILES:
        fail(
            "Android sharedViewFiles mismatch: "
            f"expected {VIEW_FILES + MIGRATION_VIEW_FILES}, got {android_views}"
        )
    android_analysis_queries = extract_pair_file_names(
        "android/app/build.gradle.kts",
        build_gradle,
        r"val\s+sharedAnalysisQueryFiles\s*=\s*listOf\((.*?)\)",
    )
    if android_analysis_queries != ANALYSIS_QUERY_FILES:
        fail(
            "Android sharedAnalysisQueryFiles mismatch: "
            f"expected {ANALYSIS_QUERY_FILES}, got {android_analysis_queries}"
        )
    for file_name in migration_files():
        if f'sharedRoot.file("migrations/{file_name}")' not in build_gradle:
            fail(f"Android SQLDelight wiring must read shared/migrations/{file_name}")
    if f"('schema_version', '{latest_schema_version()}')" not in build_gradle:
        fail(f"Android fresh install must seed schema_version {latest_schema_version()}")
    if 'sharedRoot.file("queries/$it")' not in build_gradle:
        fail("Android SQLDelight wiring must read shared/queries entries")
    if 'sharedRoot.file("queries/${it.first}")' not in build_gradle:
        fail("Android SQLDelight wiring must read shared analysis query entries")


def validate_windows_wiring() -> None:
    shared_sql = read(ROOT / "windows" / "GestorFinances.Tests" / "SharedSql.cs")
    windows_queries = extract_strings(
        "windows/GestorFinances.Tests/SharedSql.cs",
        shared_sql,
        r"ViewFiles\s*=\s*\[(.*?)\]",
    )
    if windows_queries != VIEW_FILES:
        fail(f"Windows ViewFiles mismatch: expected {VIEW_FILES}, got {windows_queries}")
    windows_migration_views = extract_strings(
        "windows/GestorFinances.Tests/SharedSql.cs",
        shared_sql,
        r"MigrationViewFiles\s*=\s*\[(.*?)\]",
    )
    if windows_migration_views != MIGRATION_VIEW_FILES:
        fail(
            "Windows MigrationViewFiles mismatch: "
            f"expected {MIGRATION_VIEW_FILES}, got {windows_migration_views}"
        )
    windows_analysis_queries = extract_strings(
        "windows/GestorFinances.Tests/SharedSql.cs",
        shared_sql,
        r"AnalysisQueryFiles\s*=\s*\[(.*?)\]",
    )
    if windows_analysis_queries != ANALYSIS_QUERY_FILES:
        fail(
            "Windows AnalysisQueryFiles mismatch: "
            f"expected {ANALYSIS_QUERY_FILES}, got {windows_analysis_queries}"
        )
    if 'ReadSharedFile("schema", "schema.sql")' not in shared_sql:
        fail("Windows SQL loader must build from shared/schema/schema.sql")
    if f"('schema_version','{latest_schema_version()}')" not in shared_sql:
        fail(f"Windows baseline must seed schema_version {latest_schema_version()}")
    if 'ReadSharedFile("queries", viewFile)' not in shared_sql:
        fail("Windows SQL loader must read shared/queries entries")


def main() -> None:
    validate_shared_inventory()
    validate_android_wiring()
    validate_windows_wiring()
    print("validated Android/Windows shared SQL parity wiring")


if __name__ == "__main__":
    main()
