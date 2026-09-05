# Design

## Status

The Android UI redesign is **complete**. Personal Compass and the implemented Android navigation, component vocabulary, themes, forms, sheets, feedback states, and accessibility behaviour are the stable product baseline.

Past redesign plans, audits, remediation checklists, phase labels, and unchecked items are retired. They are historical context only and must not be used as active requirements. Future UI changes are normal feature work or targeted maintenance, not continuation of a redesign phase.

## Implemented baseline

The current Android shell uses:

- root destinations `Inici`, `Moviments`, centered new-movement action, `Anàlisi`, and `Més`;
- a Més sheet for accounts, categories, people, trips, recurring activity, budgets, savings goals, and settings;
- an intentionally compact, single-page Analysis overview for month, year, and all-time periods, without legacy tabs or drill-down navigation;
- focused full-page movement, trip, and contextual flows;
- Compose components and semantic colours built from the shared token file;
- externalized Catalan copy;
- a locally saved System, Light, or Dark appearance choice applied across the app.

This describes the completed Android baseline. It may evolve deliberately with product needs, but new work starts from it rather than from retired redesign material.

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

- warm light/dark surface and text scales, including a subtle supporting-text step below the muted body label for metadata that sits beside a primary label;
- plum brand/interaction colours;
- functional meanings for income, expense, transfer, settlement, refund, debt, shared activity, and warnings;
- category identity colours separate from money meaning;
- Schibsted Grotesk for interface text and JetBrains Mono for financial figures;
- shared spacing, radii, elevations, sizes, icon guidance, and minimum 44 dp touch targets.

Tokens are not sacred during future feature work. When a visual decision becomes part of the product, update the JSON and native mapping together. Avoid adding a second undocumented token system in a screen.

The foundation deliberately leaves layouts, information priority within a page, components not yet shared, interaction patterns, and copy open for focused page/form work. Record a decision here only once it has proved useful beyond that page.

## Movement row

The movement row is the densest shared component in the product: it carries Inici, Moviments, and the account, category, person, trip, and recurring pages, so both platforms render the same idea.

- A run of movements is a ledger, not a stack of cards. Rows have no surface of their own and are parted by a hairline inset under the icon; the last row of a run or of a day group closes without one.
- A row is two lines, or three when it must be. The name and the amount share the first line; one qualifying line under it carries the date, whose money moved, and the category, trip, and tag. That line wraps once and stops. Whatever does not fit belongs to the movement page, not to a third qualifying line.
- The row leads with a filled tile in the category's own saved colour with the icon knocked out of it. Any movement that has a category takes that colour, including an expense someone else paid; only a movement with no category — transfer, settlement, refund — falls back to its functional colour.
- A named account or person on the qualifying line is a small mark in its saved colour followed by plain text, never a tinted container. A person takes an icon rather than a dot, so shared activity and debt never rest on colour alone.
- Weight separates the three jobs: the name in primary ink, the amount in mono figures beside it, and the qualifying line in the subtle supporting-text step, which stays above the contrast floor for small text rather than fading into the page.
- Shared and external expenses lead with the user's own share and caption the full total beneath it. The movement page states the same two numbers the same way round.

## Baseline principles

Until deliberately changed by a durable product or design decision:

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

For each new feature or targeted UI change:

1. Inspect the current screen, behaviour, data source, and tests.
2. Extend the established baseline with the smallest coherent product change.
3. Preserve the product and data invariants in [product.md](product.md) and [data-contract.md](data-contract.md).
4. Reuse proven semantic components and keep mobile and desktop layouts platform-appropriate.
5. Validate proportionally with focused tests and a real-device check when presentation or interaction matters.
6. Update this document only for a durable cross-app design decision; task status belongs in the relevant plan.

Code cleanup is not a standing phase or gate. Do it only when required by the selected outcome.
