# CLAUDE.md

@AGENTS.md

`AGENTS.md` (imported above) is the canonical agent guide — **all project rules, invariants, and workflow live there.** This file adds Claude Code specifics only.

## Claude Code notes

- **Subagents** live in `.claude/agents/`: `schema-steward`, `android-engineer`, `windows-engineer`, `ui-ux-designer`, `spec-guardian`, `simplicity-guardian`. Delegate to the matching one for focused work; run **both `spec-guardian` (correct) and `simplicity-guardian` (simple)** to review a diff before finishing.
- **Plan first for cross-cutting changes** (schema, sync, anything under `shared/`) — they ripple into both apps and the golden vectors.
- **The golden vectors (`shared/golden/`) are the test gate** — run them after any money-rule change; never weaken one to pass.
- The project is **pre-implementation**; `docs/06-roadmap.md` drives sequencing (Phase 0 = scaffolding + extracting a runnable `shared/`).

## Top invariants (quick reference — full list in AGENTS.md)

Integer cents · balances/debts derived via canonical SQL · `shared/` SQL is the single source (no EF Core) · golden vectors are law · calendar-date vs UTC instant · soft-delete only · never block (warn) · single-user / euros / offline / desktop-only CSV · Catalan UI, English code · simplest correct code (no overengineering).
