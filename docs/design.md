# Design and Redesign

## Status

The Android app is in a fresh, intentionally open-ended redesign phase.

There is no predefined redesign backlog, required page order, or promise that every screen will be replaced. The visual foundation is **Personal Compass**: warm, calm, structured personal finance software that leads with understandable actions and evidence. A change may be a complete makeover, a small polish, a behaviour correction, a component cleanup, or a related sidequest. The user chooses the next area as work progresses.

Past Android redesign plans, audits, and remediation checklists are retired. Their unchecked items are not carried forward.

## Starting point

The current Android shell uses:

- root destinations `Inici`, `Moviments`, centered new-movement action, `Anàlisi`, and `Més`;
- a Més sheet for accounts, categories, people, trips, recurring activity, budgets, and settings;
- an intentionally compact, single-page Analysis overview for month, year, and all-time periods, without legacy tabs or drill-down navigation;
- focused full-page movement, trip, and contextual flows;
- Compose components and semantic colours built from the shared token file;
- externalized Catalan copy;
- a locally saved System, Light, or Dark appearance choice applied across the app.

This describes the implemented starting point, not a constraint on future navigation or presentation.

Selecting a destination through the bottom bar or Més intentionally resets it to its default context. Contextual links remain separate flows and may preserve caller-specific state and Back behavior.

## Established visual direction — Personal Compass

Personal Compass makes detailed personal finance feel calm and human without becoming decorative, playful, or bank-like. It is a visual foundation, not a screen-layout or navigation specification.

- **Warm, private, and precise.** Sandstone page surfaces, warm-white cards, and dark plum text replace cool clinical neutrals. Visual warmth comes from material and colour, never from lifestyle photography or ornament.
- **Clear action, then context.** A page may lead with what needs attention, then show the financial evidence behind it. This is a priority principle, not a prescribed dashboard order.
- **Evidence over decoration.** Currency uses tabular mono figures; charts are compact and paired with labels or lists; a concise written signal is preferable to a dense collection of visualizations.
- **Gentle structure.** Use opaque layered surfaces, quiet borders, modest elevation, and 16–18 dp large cards/sheets. Avoid gradients, glass effects, oversized shadows, neon, or trading-terminal density.
- **Meaning is explicit.** Colour reinforces a labelled state; it never carries financial direction, debt, budget status, or shared activity on its own. Risky but valid finance situations remain informative, not punitive.
- **Platform-native restraint.** Use Material Symbols and Android-appropriate controls. The centred new-movement action remains a product convention; the surrounding layout is decided screen by screen.

The explored mockups illustrate the direction only. They do not establish a universal greeting, a fixed home-page composition, navigation changes, drafts, new product states, or exact copy.

Use a shared component vocabulary where the contract is genuinely shared: cards, list rows, pickers, sheets, menus, filters, and feedback states should look and behave consistently across pages. Reuse the semantic components in `ui/common`; promote patterns proven on more than one screen, but do not create broad generic wrappers or force one-off layouts into a component.

## Current token authority

Platform-neutral values live in `shared/design/tokens/design-tokens.json`. Android maps them into semantic Compose names under `ui/theme`; the future Windows app will map the same intent into WinUI resources.

The current baseline contains:

- warm light/dark surface and text scales;
- plum brand/interaction colours;
- functional meanings for income, expense, transfer, settlement, refund, debt, shared activity, and warnings;
- category identity colours separate from money meaning;
- Geist for interface text and IBM Plex Mono for financial figures;
- shared spacing, radii, elevations, sizes, icon guidance, and minimum 44 dp touch targets.

Tokens are not sacred during redesign. When a visual decision becomes part of the product, update the JSON and native mapping together. Avoid adding a second undocumented token system in a screen.

The foundation deliberately leaves layouts, information priority within a page, components not yet shared, interaction patterns, and copy open for focused page/form work. Record a decision here only once it has proved useful beyond that page.

## Baseline principles

Until deliberately changed for a specific redesign decision:

- financial meaning must be understandable without relying on colour alone;
- actual values and account flow must be named honestly;
- risky actions show understandable warnings near the relevant action;
- primary actions remain reachable with the keyboard open;
- empty, loading, error, disabled, and read-only states are explicit;
- failed loads explain the affected action in Catalan and offer Retry; save failures keep the entered form values and use concise action-specific copy, never raw technical exception text;
- layouts support narrow Android widths, text scaling, TalkBack, and dark surfaces;
- Settings offers System (default), Light, and Dark appearance modes. The saved choice drives the root palette, system bars, banners, charts, dialogs, sheets, and semantic component colours as one theme;
- saved account and category identity hues remain unchanged; dark rendering raises only overly dark hues so icons and proportional bars stay legible on dark surfaces;
- movement filtering keeps expense, income, and transfer immediately visible; settlement, refund, and external expense sit in one compact “More types” menu rather than enlarging the filter sheet;
- destructive confirmation may use a dialog; user-facing copy consistently says Delete, and a completed normal finance deletion offers snackbar Undo for the whole operation; compact editing generally prefers a sheet or focused page;
- every editable form compares its current values with the values it opened with: Back, Cancel, sheet swipe-away, and outside-tap close untouched forms immediately and ask before discarding meaningful changes; validation and disclosure-only state does not count as a change;
- focused, longer forms and bounded short sheets both keep their save action reachable while the body scrolls; IME Next advances, Done submits, and field validation scrolls to and focuses the first invalid control;
- modal sheets use the shared Android sheet contract: one content-sized expanded state with
  optional screen-relative height bounds; long forms scroll their body while keeping the primary action
  reachable, with standard safe-area and keyboard handling, token-aligned shape and scrim, and
  animated dismissal before backing state or navigation is removed;
- mobile and desktop share product language but use platform-appropriate layouts.

## Working method

For each chosen page, feature, or component:

1. Inspect the current screen, behaviour, data source, and tests.
2. Use the established foundation, then decide only the outcome needed for that page or form. Do not invent a phase-wide layout backlog.
3. Follow useful sidequests when they improve the selected outcome, including logic or code cleanup.
4. Preserve the product and data invariants in [product.md](product.md) and [data-contract.md](data-contract.md).
5. Validate proportionally: focused tests and a real-device check when presentation or interaction matters.
6. Update this document only for a durable cross-app design decision. Do not turn it into a task tracker.

Code cleanup may happen before or during redesign. It is not a mandatory gate unless the selected work genuinely needs it.
