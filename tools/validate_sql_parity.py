#!/usr/bin/env python3
"""Validate that app SQL inputs are wired from shared SQL artifacts."""

from __future__ import annotations

import re
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


def fail(message: str) -> None:
    print(f"SQL parity validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def read(path: Path) -> str:
    if not path.exists():
        fail(f"missing file: {path}")
    return path.read_text(encoding="utf-8")


def extract_strings(label: str, text: str, pattern: str) -> list[str]:
    match = re.search(pattern, text, flags=re.DOTALL)
    if not match:
        fail(f"{label}: could not find expected SQL file list")
    return re.findall(r'"([^"]+)"', match.group(1))


def validate_shared_inventory() -> None:
    schema = ROOT / "shared" / "schema" / "schema.sql"
    migration = ROOT / "shared" / "migrations" / "001_initial.sql"
    if not schema.exists():
        fail(f"missing shared schema: {schema}")
    if not migration.exists():
        fail(f"missing shared migration: {migration}")

    actual_queries = sorted(path.name for path in (ROOT / "shared" / "queries").glob("*.sql"))
    if actual_queries != sorted(QUERY_ORDER):
        fail(f"shared query inventory mismatch: expected {sorted(QUERY_ORDER)}, got {actual_queries}")


def validate_android_wiring() -> None:
    build_gradle = read(ROOT / "android" / "app" / "build.gradle.kts")
    android_queries = extract_strings(
        "android/app/build.gradle.kts",
        build_gradle,
        r"val\s+sharedQueryFiles\s*=\s*listOf\((.*?)\)",
    )
    if android_queries != QUERY_ORDER:
        fail(f"Android sharedQueryFiles mismatch: expected {QUERY_ORDER}, got {android_queries}")
    if 'sharedRoot.file("migrations/001_initial.sql")' not in build_gradle:
        fail("Android SQLDelight wiring must read shared/migrations/001_initial.sql")
    if 'sharedRoot.file("queries/$it")' not in build_gradle:
        fail("Android SQLDelight wiring must read shared/queries entries")


def validate_windows_wiring() -> None:
    shared_sql = read(ROOT / "windows" / "GestorFinances.Tests" / "SharedSql.cs")
    windows_queries = extract_strings(
        "windows/GestorFinances.Tests/SharedSql.cs",
        shared_sql,
        r"ViewFiles\s*=\s*\[(.*?)\]",
    )
    if windows_queries != QUERY_ORDER:
        fail(f"Windows ViewFiles mismatch: expected {QUERY_ORDER}, got {windows_queries}")
    if 'ReadSharedFile("migrations", "001_initial.sql")' not in shared_sql:
        fail("Windows SQL loader must read shared/migrations/001_initial.sql")
    if 'ReadSharedFile("queries", viewFile)' not in shared_sql:
        fail("Windows SQL loader must read shared/queries entries")


def main() -> None:
    validate_shared_inventory()
    validate_android_wiring()
    validate_windows_wiring()
    print("validated Android/Windows shared SQL parity wiring")


if __name__ == "__main__":
    main()
