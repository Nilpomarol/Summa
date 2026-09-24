# AGENTS.md — Summa

Canonical operating guide for coding agents. `CLAUDE.md` imports this file.

## Project state

Summa is a private, local-first personal finance app for one person, euros only, with a Catalan UI. Android (Kotlin/Compose) is the working product and current implementation authority. Windows is future product work; its current code is only a shared-contract test harness.

The Android database may contain real user data. Never clear, replace, or seed it unless the user explicitly approves an isolated test-data operation.

## Read only what the task needs

Start here, then open the smallest relevant source/doc set:

- `docs/product.md` — durable product behaviour and finance meaning.
- `docs/data-contract.md` — shared schema and canonical finance SQL.
- `docs/design.md` — durable Android design/interaction baseline.
- `docs/pre-windows-plan.md` — current product backlog before Windows.
- `docs/architecture.md` / `docs/windows-plan.md` — only when the task actually concerns those future areas.

Do not treat retired plans, audits, completed implementation diaries, or speculative future designs as requirements.

## Non-negotiable invariants

1. Store money as integer euro cents; format euros only at the UI edge.
2. Balances, debts, account flow, actual income/expense, and trip totals come from canonical SQL; do not create competing finance truth in app code.
3. Movement `date` is local `YYYY-MM-DD`; `*_at` fields are UTC instants.
4. Normal finance deletion is recoverable soft deletion via `archived_at`.
5. Risky but valid actions warn and allow confirmation; structurally invalid records remain errors.
6. Keep the product single-owner, euro-only, and local-first. Shared accounts do not create app users/authentication.
7. User-facing copy is resource-backed Catalan; code, identifiers, comments, and technical docs are English.
8. Preserve schema constraints, including positive `amount_cents` and movement type/field integrity.
9. Never damage or silently replace real user data.

## Working style

- Prefer the smallest correct change that fits the current product. Do not build seams, abstractions, interfaces, flags, or infrastructure only for hypothetical future work.
- Ordinary localized work is done directly by the current coding session. Do not delegate by default.
- Keep flows easy to trace. Prefer concrete repositories/services and focused helpers over generic frameworks or pass-through layers.
- Reuse established UI components when they genuinely match; do not generalize one-off layouts prematurely.
- Fix logic at the deepest appropriate layer, but do not expand a local task into an architecture rewrite without a concrete benefit.
- Work in thin vertical slices and preserve unrelated/uncommitted user changes.
- Update durable docs only when durable behaviour changes. Do not record implementation history in permanent docs.
- Add focused tests proportional to risk. Run broader suites only when the changed surface warrants them.
- Do not commit, push, clear data, or create a branch unless asked.

## Shared-contract changes

A change is a shared-contract change only when it modifies schema, migrations, canonical finance SQL, or a finance rule intentionally shared across platforms.

For those changes:

- keep fresh schema + migration + canonical query behaviour consistent;
- update focused golden cases when the financial result changes;
- update Android bindings/tests;
- keep the Windows contract harness green when it exercises the affected shared rule;
- update concise durable documentation.

Do not require Windows-specific implementation work for ordinary Android changes.

## Optional specialists

There are only two specialist roles. They are optional.

- `reviewer` — read-only review of substantial/risky changes for correctness, regression risk, and unnecessary complexity.
- `schema-steward` — implementation specialist for genuine shared schema/migration/canonical-SQL changes.

Use neither for a routine localized task. Use `reviewer` when an independent pass materially reduces risk. Use `schema-steward` only when the data contract itself changes.

## Build and test

Android, from `android/`:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Shared Windows harness, only when relevant to shared-contract work:

```powershell
dotnet test .\windows\GestorFinances.Tests\GestorFinances.Tests.csproj
```
