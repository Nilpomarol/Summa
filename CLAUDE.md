# CLAUDE.md

@AGENTS.md

`AGENTS.md` is the canonical project guide.

Claude Code subagents live in `.claude/agents/`. They are optional: use the smallest relevant set
when specialization materially helps, and handle ordinary localized work directly. Plan
cross-cutting schema/shared work before editing. Request correctness or simplicity review in
proportion to the change's risk; do not invoke both automatically for every task.
