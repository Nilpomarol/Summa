#!/usr/bin/env python3
"""Validate shared golden fixture envelopes."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
GOLDEN_DIR = ROOT / "shared" / "golden"
SCHEMA_DIR = ROOT / "shared" / "schemas"


def fail(message: str) -> None:
    print(f"golden validation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"{path}: invalid JSON: {exc}")


def validate_cents(path: Path, value: Any, trail: str = "$") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            child_trail = f"{trail}.{key}"
            if key.endswith("_cents"):
                validate_cents_value(path, child, child_trail)
            validate_cents(path, child, child_trail)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            validate_cents(path, child, f"{trail}[{index}]")


def validate_cents_value(path: Path, value: Any, trail: str) -> None:
    if value is None:
        return
    if isinstance(value, bool):
        fail(f"{path}: {trail} must contain integer cents, not a boolean")
    if isinstance(value, int):
        return
    if isinstance(value, dict):
        for key, child in value.items():
            validate_cents_value(path, child, f"{trail}.{key}")
        return
    if isinstance(value, list):
        for index, child in enumerate(value):
            validate_cents_value(path, child, f"{trail}[{index}]")
        return
    fail(f"{path}: {trail} must contain integer cents")


def validate_fixture(path: Path) -> str:
    data = load_json(path)
    if not isinstance(data, dict):
        fail(f"{path}: top-level value must be an object")

    required = {"rule", "description", "cases"}
    allowed = required | {"params"}
    actual = set(data.keys())
    missing = required - actual
    unknown = actual - allowed
    if missing or unknown:
        fail(
            f"{path}: missing top-level keys {sorted(missing)}, "
            f"unknown top-level keys {sorted(unknown)}"
        )

    rule = data["rule"]
    if not isinstance(rule, str) or not rule:
        fail(f"{path}: rule must be a non-empty string")
    if rule != path.stem:
        fail(f"{path}: rule '{rule}' must match file stem '{path.stem}'")

    description = data["description"]
    if not isinstance(description, str) or not description.strip():
        fail(f"{path}: description must be a non-empty string")

    cases = data["cases"]
    if not isinstance(cases, list) or not cases:
        fail(f"{path}: cases must be a non-empty array")

    if "params" in data and not isinstance(data["params"], dict):
        fail(f"{path}: params must be an object when present")

    seen_names: set[str] = set()
    for index, case in enumerate(cases):
        if not isinstance(case, dict):
            fail(f"{path}: cases[{index}] must be an object")
        case_keys = set(case.keys())
        required_case_keys = {"name", "input", "expected"}
        if case_keys != required_case_keys:
            fail(
                f"{path}: cases[{index}] keys must be exactly "
                f"{sorted(required_case_keys)}, got {sorted(case_keys)}"
            )

        name = case["name"]
        if not isinstance(name, str) or not name.strip():
            fail(f"{path}: cases[{index}].name must be a non-empty string")
        if name in seen_names:
            fail(f"{path}: duplicate case name '{name}'")
        seen_names.add(name)

        if not isinstance(case["input"], dict):
            fail(f"{path}: case '{name}' input must be an object")
        if not isinstance(case["expected"], dict):
            fail(f"{path}: case '{name}' expected must be an object")

    validate_cents(path, data)
    return rule


def main() -> None:
    fixture_paths = sorted(GOLDEN_DIR.glob("*.json"))
    if not fixture_paths:
        fail(f"{GOLDEN_DIR}: no golden fixtures found")

    seen_rules: set[str] = set()
    for path in fixture_paths:
        rule = validate_fixture(path)
        if rule in seen_rules:
            fail(f"{path}: duplicate rule '{rule}'")
        seen_rules.add(rule)

    for path in sorted(SCHEMA_DIR.glob("*.json")):
        schema = load_json(path)
        if not isinstance(schema, dict):
            fail(f"{path}: schema must be a JSON object")

    print(f"validated {len(fixture_paths)} golden fixture files")


if __name__ == "__main__":
    main()
