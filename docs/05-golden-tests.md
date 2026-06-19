# Personal Finance App — Golden Test Vectors

> The **executable contract** between the two native apps (arch `03-architecture.md` §4.2). A small set of money rules is re-implemented in each language (Kotlin/Android, C#/Windows); these language-neutral fixtures pin each rule as *input → expected output* so both implementations must produce identical results. SQL-derivation rules (flow, debt, actual) are shared verbatim, but their fixtures here double as integration checks that both DB layers load the schema and run the canonical SQL identically.

## Location & consumption

- Fixtures live under **`shared/golden/`** as JSON, one file per rule. This is the shared-artifact location (alongside the eventual `schema.sql` and canonical-SQL library, arch §5).
- Each app's test suite loads the **same files** and asserts its implementation matches `expected` for every case. A divergence is a failing test, not a silent money bug.

## File envelope

```json
{
  "rule": "<rule-id>",
  "description": "<what this pins>",
  "params": { "...": "optional rule-level constants" },
  "cases": [
    { "name": "<case>", "input": { ... }, "expected": { ... } }
  ]
}
```

Validate the shared fixture envelopes with:

```bash
python3 tools/validate_golden.py
```

The validator checks the common envelope, requires each `rule` to match its file name, enforces unique case names, and verifies `*_cents` fields are JSON integers.

## Global conventions

- **Money** is integer **euro cents** (data-model §1).
- **Calendar dates** are `'YYYY-MM-DD'`; **instants** are ISO-8601 UTC.
- **Ids** are opaque strings (UUIDs in production; short labels like `"acc-1"`, `"pA"` in fixtures).
- Procedural rules are **pure functions**; SQL-scenario fixtures provide rows and expect derived values from the §7 views.

## The fixtures

| File | Pins | Kind |
|---|---|---|
| `split_rounding.json` | equal / percentage / exact splits → absolute cents; remainder cent(s) to the **payer** (spec §3.7, §2.4) | procedural |
| `recurring_advance.json` | due-occurrence generation + cursor advance, incl. **month-end / leap clamping** (spec §3.10) | procedural |
| `auto_categorize.json` | rule matching, priority, **newer-wins** tie-break (spec §3.15, §4.4) | procedural |
| `duplicate_detection.json` | the v1 dedup heuristic (spec §4.6) — **see thresholds below** | procedural |
| `refund_actual.json` | refund **cash inflow vs. actual reduction** via `actual_refund_cents` (data-model §2; spec §3.3b) | SQL/semantic |
| `debt_balance.json` | per-person balance from splits + settlements (`v_person_balance`, spec §4.1) | SQL scenario |
| `account_flow.json` | `current_balance` incl. **transfer dual-sign** & settlement direction (`v_account_balance`, spec §4.2) | SQL scenario |

## Rule definitions (the contract, in prose)

### Split rounding (spec §3.7)
- **equal:** `base = total // n`, `rem = total % n`; every share = `base`, then the **payer's** share += `rem`. Σ shares = total exactly.
- **percentage:** weights given as **basis points** (integers summing to 10000). `share_i = (total * bps_i) // 10000`; any leftover cents from flooring go to the **payer**. Σ = total.
- **exact:** amounts are stored as given; validation requires `Σ = total` (a non-reconciling set is rejected).

### Recurring advancement (spec §3.10)
Given a template (`frequency`, `day_of_month` anchor for monthly/yearly, `interval_count`+`custom_unit` for custom), a `cursor` (= `next_due_date`), and `today`: emit every occurrence date `≤ today` starting at `cursor`, and return the new cursor (first occurrence `> today`). **Monthly/yearly clamp to the month's last day from the anchor day each period** (so day-31 yields Jan 31 → Feb 28 → Mar 31, *not* Mar 28). Weekly = +7d, fortnightly = +14d, custom = +`interval_count`×`custom_unit`.

### Auto-categorization match (spec §3.15, §4.4)
Conditions JSON (all present keys must match, AND-combined; empty = matches all):
```json
{ "text_contains": "<substring, case-insensitive, matched against name + ' ' + payee>",
  "amount_min_cents": 0, "amount_max_cents": 0,        // inclusive range; either optional
  "account_id": "<exact>", "day_of_month_in": [1,15] } // optional
```
JSON Schema: `shared/schemas/auto_cat_rules.conditions.schema.json`.

Action JSON: `{ "set_category_id": "<id>", "set_trip_id": "<id>" }` (either optional). Among **active** rules that match, the **highest `priority`** wins; on a tie, the **most recent `created_at`** wins. No match → no action (and a manually-set category is never overridden — enforced at apply time, not by the matcher).

### Duplicate detection (spec §4.6) — **[DECISION — tunable defaults]**
The spec left "near amount / near date / similar description" vague. v1 default heuristic (deterministic): a candidate is a likely duplicate of an existing movement iff **all** hold:
1. same `account_id`;
2. **equal** `amount_cents`;
3. `|date − date|` ≤ **1 day**;
4. **normalized name equal**, where normalize = lowercase, trim, collapse internal whitespace.

Never blocks — only flags (spec §4.6). Adjust the amount tolerance / day window / name comparison here and the fixtures follow.

## `templates.split_config` JSON schema (data-model §6)
Pre-fill payload carried forward for a recurring **shared** expense (the real split is created at materialization):
```json
{ "entry_method": "equal" | "exact" | "percentage",
  "payer": "user" | "<person_id>",
  "lines": [ { "party": "user" | "<person_id>", "owed_amount_cents": 0 } ] }
```

JSON Schema: `shared/schemas/templates.split_config.schema.json`.

---

These cover the core money rules; the set is meant to grow (more edge cases per rule) as the apps are built.
