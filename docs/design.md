# Design and Redesign

## Status

The Android app is in a fresh, intentionally open-ended redesign phase.

There is no predefined redesign backlog, target aesthetic, required page order, or promise that every screen will be replaced. A change may be a complete makeover, a small polish, a behaviour correction, a component cleanup, or a related sidequest. The user chooses the next area as work progresses.

Past Android redesign plans, audits, and remediation checklists are retired. Their unchecked items are not carried forward.

## Starting point

The current Android shell uses:

- root destinations `Inici`, `Moviments`, centered new-movement action, `Anàlisi`, and `Gestió`;
- a Management hub for accounts, categories, people, trips, recurring activity, and settings;
- focused full-page movement, trip, and contextual flows;
- Compose components and semantic colours built from the shared token file;
- externalized Catalan copy;
- light and dark token mappings, although the present app may choose one default theme.

This describes the implemented starting point, not a constraint on future navigation or presentation.

## Current token authority

Platform-neutral values live in `shared/design/tokens/design-tokens.json`. Android maps them into semantic Compose names under `ui/theme`; the future Windows app will map the same intent into WinUI resources.

The current baseline contains:

- neutral light/dark surface and text scales;
- indigo brand/interaction colours;
- functional meanings for income, expense, transfer, settlement, refund, debt, shared activity, and warnings;
- category identity colours separate from money meaning;
- Geist for interface text and IBM Plex Mono for financial figures;
- shared spacing, radii, elevations, sizes, icon guidance, and minimum 44 dp touch targets.

Tokens are not sacred during redesign. When a visual decision becomes part of the product, update the JSON and native mapping together. Avoid adding a second undocumented token system in a screen.

## Baseline principles

Until deliberately changed for a specific redesign decision:

- financial meaning must be understandable without relying on colour alone;
- actual values and account flow must be named honestly;
- risky actions show understandable warnings near the relevant action;
- primary actions remain reachable with the keyboard open;
- empty, loading, error, disabled, and read-only states are explicit;
- layouts support narrow Android widths, text scaling, TalkBack, and dark surfaces;
- destructive confirmation may use a dialog; compact editing generally prefers a sheet or focused page;
- mobile and desktop share product language but use platform-appropriate layouts.

## Working method

For each chosen page, feature, or component:

1. Inspect the current screen, behaviour, data source, and tests.
2. Decide only the outcome needed for that item; do not invent a phase-wide backlog.
3. Follow useful sidequests when they improve the selected outcome, including logic or code cleanup.
4. Preserve the product and data invariants in [product.md](product.md) and [data-contract.md](data-contract.md).
5. Validate proportionally: focused tests and a real-device check when presentation or interaction matters.
6. Update this document only for a durable cross-app design decision. Do not turn it into a task tracker.

Code cleanup may happen before or during redesign. It is not a mandatory gate unless the selected work genuinely needs it.
