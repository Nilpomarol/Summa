# CLAUDE.md

@AGENTS.md

`AGENTS.md` (imported above) is the canonical agent guide — **all project rules, invariants, and workflow live there.** This file adds Claude Code specifics only.

## Claude Code notes

- **Subagents** live in `.claude/agents/`: `schema-steward`, `android-engineer`, `windows-engineer`, `ui-ux-designer`, `spec-guardian`, `simplicity-guardian`. Delegate to the matching one for focused work; run **both `spec-guardian` (correct) and `simplicity-guardian` (simple)** to review a diff before finishing.
- **Plan first for cross-cutting changes** (schema, sync, anything under `shared/`) — they ripple into both apps and the golden vectors.
- **The golden vectors (`shared/golden/`) are the test gate** — run them after any money-rule change; never weaken one to pass.
- `docs/06-roadmap.md` drives sequencing. Phase 0 (0A–0C), Android P1-1 through P1-12, Phase 2 (P2-1 through P2-11), and all of Phase 3 (P3-1 through P3-11) are complete. Phase 4 is underway: P4-1 (UI/UX + strings, `docs/13`), P4-2 (templates CRUD: `Templates.sq`, `TemplateRepository`, the `Recurring` screen/nav; kotlinx.serialization in app `main`), P4-3 (recurring due prompts: confirm→create linked movement + advance cursor, skip, skip-all, split carry-forward), P4-4 (recurring list day-ordered + monthly summary card), P4-5 (refunds from expense detail), and P4-6 (budgets — category-monthly CRUD + limit-vs-actual evaluation + green/amber/red progress, reached from the Analysis header) are done. P4-7 (local notifications/alerts) is next unless the roadmap says otherwise. Phase 5R is the dedicated Android redesign/consolidation checkpoint before Windows starts.

## Top invariants (quick reference — full list in AGENTS.md)

Integer cents · balances/debts derived via canonical SQL · `shared/` SQL is the single source (no EF Core) · golden vectors are law · calendar-date vs UTC instant · soft-delete only · never block (warn) · single-user / euros / offline / desktop-only CSV · Catalan UI, English code · simplest correct code (no overengineering).
