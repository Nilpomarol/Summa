# AGENTS.md — Gestor Finances

Canonical operating guide for coding agents. `CLAUDE.md` imports this file.

## Project state

Gestor Finances is an offline-first, local-first personal finance app for one person, euros only, with a Catalan UI. Android (Kotlin/Compose) is the working primary app and database owner. Windows currently has a .NET contract test harness; the WinUI app is future work.

Android is in an intentionally open-ended redesign phase. There is no phase backlog or mandatory page order. Work on the page, feature, component, or sidequest the user selects. Do not resurrect retired roadmap or audit tasks.

The Android database may contain real user data. Never clear, replace, or seed it unless the user explicitly approves an isolated test-data operation.

## Active documentation

- `README.md` — entry point and current state.
- `docs/product.md` — current capabilities and durable product behaviour.
- `docs/data-contract.md` — shared schema, SQL, migrations, and golden rules.
- `docs/architecture.md` — native app boundaries, backup, and future sync.
- `docs/design.md` — current baseline and redesign working method.
- `docs/windows-plan.md` — preserved Windows implementation plan.
- `shared/design/tokens/design-tokens.json` — machine-readable visual tokens.

Keep documentation concise and current. Record durable behaviour or decisions, not implementation diaries, completed-task histories, speculative backlogs, or per-session checklists.

## Non-negotiable invariants

1. **Integer cents:** all money is integer euro cents. Format euros only at the UI edge.
2. **Derived finance truth:** balances, debts, account flow, actual income/expense, and trip totals come from canonical SQL views. Never store or recompute them independently in app code.
3. **One shared contract:** `shared/` owns schema and canonical SQL. Android consumes it through SQLDelight; Windows uses Microsoft.Data.Sqlite and Dapper. No EF Core finance queries.
4. **Golden rules:** changes to split rounding, template rescaling, recurrence, categorization, duplicate detection, refunds, debt, or account flow update the golden vector first and then both platforms.
5. **Date types:** movement `date` is a local `YYYY-MM-DD` calendar date; `*_at` fields are UTC instants.
6. **Soft deletion:** normal flows archive with `archived_at`; they do not hard-delete finance data.
7. **Warn for risky valid actions:** duplicates, over-refunds, excess settlements, and dependency warnings remain dismissible. Structural database invalidity is still an error.
8. **Product scope:** single user, euros, offline/local database, no auth/profiles, no cloud DB, and CSV import on Windows only.
9. **Language:** externalized Catalan UI; English code, identifiers, comments, and technical docs.
10. **Single-writer sync:** when implemented, only the token holder writes; the other device is read-only. Snapshots are consistent, encrypted, versioned, and atomically applied. Never merge.
11. **Movement integrity:** `amount_cents > 0`; type and related fields must satisfy schema constraints.

## Working rules

- Read the relevant active document before changing its area.
- Prefer the simplest correct implementation matching existing patterns. Avoid speculative abstraction, new dependencies, parallel frameworks, and one-off helpers.
- A UI discovery that exposes incorrect logic is fixed at the deepest correct layer: shared contract, repository/domain, ViewModel, UI, strings, tests, and docs as affected.
- Schema changes are atomic: fresh DDL, new migration and version bump, embedded/canonical views, golden vectors where relevant, Android bindings, Windows harness, tests, and concise docs.
- Work in thin vertical slices. Preserve unrelated and uncommitted user changes.
- User-facing strings are resource-backed. Use shared tokens semantically, but redesign may intentionally change them by updating the JSON and native mapping together.
- Keep recurring UI patterns consistent: inspect and reuse the semantic components in `ui/common` for cards, rows, pickers, sheets, menus, filters, and feedback states. When a pattern has the same visual and interaction contract on more than one screen, promote it to a focused shared component; do not create generic wrappers or abstractions for one-off layouts.
- Add manual checks with expected results when the user can exercise changed behaviour; keep them in the handoff or focused tests, not as a permanent roadmap.
- Do not commit, push, clear data, or create a branch unless asked.

## Optional specialist roles

Roles are tools, not required ceremony. Use the smallest relevant set for the selected work. A
localized, low-risk change can be handled directly without a subagent or separate review pass.
Use specialist review when it materially reduces risk: `spec-guardian` for shared contracts,
money rules, data integrity, or broad product behaviour; `simplicity-guardian` for substantial
refactors, new abstractions, or unusually complex changes.

- `schema-steward` — schema, migrations, canonical SQL, and golden vectors.
- `android-engineer` — Kotlin, Compose, SQLDelight, and Android tests.
- `windows-engineer` — C#, WinUI 3, Microsoft.Data.Sqlite/Dapper, and desktop CSV import.
- `ui-ux-designer` — screen/flow design and Catalan copy; mobile and desktop layouts remain platform-appropriate.
- `spec-guardian` — read-only correctness review against active contracts.
- `simplicity-guardian` — read-only review for unnecessary complexity.

## Build and test

Android, from `android/`:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Windows/shared harness, from the repository root:

```powershell
dotnet test .\windows\GestorFinances.Tests\GestorFinances.Tests.csproj
```

Any red golden test blocks delivery of a money-rule or shared-contract change.
