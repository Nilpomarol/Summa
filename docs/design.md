# Design

## Direction

The Android visual direction is **Personal Compass**: warm, private, precise, and evidence-led. It should feel like a personal tool, not a bank dashboard or trading terminal.

## Durable principles

- Financial meaning is explicit and never depends on colour alone.
- Prefer calm layered surfaces, restrained elevation, and compact charts over decorative effects.
- Currency uses tabular/monospaced figures where practical.
- Reuse semantic components when the same interaction genuinely repeats; do not generalize one-off layouts.
- User-facing copy is Catalan and resource-backed.
- Support narrow widths, text scaling, TalkBack, light/dark/system themes, keyboard-safe forms, and explicit loading/error/empty/disabled states.
- Save failures keep entered values and show concise user-facing feedback.
- Destructive finance actions use understandable confirmation when needed and successful soft deletion offers Undo.
- Forms protect meaningful unsaved changes.

## Navigation baseline

The current Android root destinations are `Inici`, `Moviments`, centered new-movement action, `Anàlisi`, and `Més`. Selecting a root destination starts from that destination's normal context; contextual navigation may preserve a Back path.

This is a current baseline, not a permanent architecture constraint. Change it deliberately when product needs justify it.

## Movement presentation

Movement lists should make the primary financial meaning obvious:

- personal/global contexts normally lead with the app owner's economic amount;
- an account ledger leads with that account's signed physical delta so the list reconciles with its balance;
- shared/external activity labels total, owner share, funding source, allocation, or debt effect when those meanings differ;
- dense secondary metadata should not overwhelm the primary name and amount.

Use the existing movement-row component as the starting point, but do not preserve layout details that no longer improve usability.

## Tokens and components

Current design tokens live in `shared/design/tokens/design-tokens.json` and map to Android theme semantics. Treat them as the current palette/system, not an immutable cross-platform contract.

Prefer existing focused components in `ui/common` when they fit. Promote a pattern only after it proves useful in more than one place.

## Working method

For UI work:

1. inspect the current screen and behaviour;
2. make the smallest coherent improvement;
3. preserve finance/product semantics;
4. reuse existing patterns when useful;
5. verify on a device when interaction or layout matters;
6. update this document only for durable, cross-screen principles.

Completed redesign plans and old UI audits are historical context, not active requirements.
