# Design

## Direction

The Android visual direction is **Personal Compass**: warm, private, precise, and evidence-led. It should feel like a personal tool, not a bank dashboard or trading terminal.

## Durable principles

- Financial meaning is explicit and never depends on colour alone.
- Prefer calm layered surfaces, restrained elevation, and compact charts over decorative effects.
- Currency uses tabular/monospaced figures where practical.
- Reuse semantic components when the same interaction genuinely repeats; do not generalize one-off layouts.
- User-facing copy is Catalan and resource-backed.
- Introduce only what the current task needs. First-time setup creates a personal bank account from a name and starting balance, with starter categories provided automatically. Advanced finance concepts are disclosed in context rather than taught during onboarding.
- Support narrow widths, text scaling, TalkBack, light/dark/system themes, keyboard-safe forms, and explicit loading/error/empty/disabled states.
- Save failures keep entered values and show concise user-facing feedback.
- Destructive finance actions use understandable confirmation when needed and successful soft deletion offers Undo.
- Forms protect meaningful unsaved changes.

## Navigation baseline

The current Android root destinations are `Inici`, `Moviments`, centered new-movement action, `Anàlisi`, and `Més`. Selecting a root destination starts a new visit: the stack becomes `Inici` plus that destination. Every other page is pushed on top of the current one, so Back always returns to where it was opened from, and the highlighted tab stays the one whose stack is showing.

`Més` is a page that groups every secondary area: Diners (`Comptes`, `Objectius`), Planificació (`Pressupostos`, `Recurrents`), Organització (`Categories`, `Viatges`, `Etiquetes`), then `Persones` and `Configuració`. Contextual shortcuts remain (goals from the account page they save in, tags from Trips, recurring from Movements, budgets from the dashboard; a trip or category edits its own budget in a sheet on its page; categories from the movement category picker). The category picker also creates a category in a sheet over the movement form, without leaving it.

Anything with its own content is its own page (for example a person or an account ledger), not a swap inside another page. Creating or editing opens a sheet over the page that asked for it. Entity forms share one layout: the entity's mark beside its name (the mark opens its colour and icon), the main fields, the rare ones under `Més detalls`, and Cancel·la / save pinned below; archiving lives in the page's menu, not in the form.

This is a current baseline, not a permanent architecture constraint. Change it deliberately when product needs justify it.

## Movement presentation

Global Add presents only `Despesa`, `Ingrés`, and `Transferència`. The form leads with the category tile beside the concept (as typed in its field below, or what is being added) over the amount (grouped as it is typed, e.g. `1.234,56 €`); then the concept field; then two lines of fields: category, then account beside the date, or for transfers origin beside destination, then the date. All three types have the same height until `Més detalls` opens its fields on one level: who paid, sharing, trip/tag, recurrence, one-time, payee, and notes; closed, it names what they hold. When a person paid, the account field stays in place, locked, and names them. Long choice lists scroll within a capped menu. Swiping away or tapping outside a form with unsaved changes asks before discarding. Existing or contextually required configuration is disclosed without clearing hidden values. Choosing a trip replaces only the default account a new form starts with. Shared-account income always asks whose money it is; contributions and withdrawals start from that account rather than being inferred from a generic transfer.

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
