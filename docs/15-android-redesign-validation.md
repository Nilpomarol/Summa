# Android Phase 5R - Redesign and Logic Validation

> Scope: Phase 5R. This is the working contract for the Android redesign checkpoint before Windows starts. It combines two tasks that must happen together: validating app logic and redesigning the Android UI/UX into a coherent, design-system-aligned product. Read this before starting any `P5R-*` task.

---

## 1. Purpose

Phase 5R is not a light polish pass. It is the point where the Android app is allowed to change deeply because the current data is test data and no user data needs compatibility preservation.

The goal is to leave Android as:

- logically correct against `docs/00-Full_Spec.md`, `docs/04-data-model.md`, and the golden vectors;
- simple and local-first, with the existing architecture principles preserved;
- visually coherent and ergonomic enough to become the source for the Windows implementation;
- free of dead fields, placeholder concepts, and UI-only patches that hide model problems.

---

## 2. Non-Negotiable Phase 5R Rules

### Deep Logic Fixes

When a logic decision changes a concept, fix it at the deepest correct layer. Do not patch around it in the UI.

If a field, entity, rule, or workflow is removed or reshaped, update every affected layer in the same change:

- docs and roadmap notes;
- shared schema, canonical SQL, migrations, and schema version when required;
- SQLDelight queries and Android generated bindings;
- repositories, ViewModels, UI state, and Compose screens;
- strings and manual checklists;
- Android tests, shared golden vectors, and Windows harness bindings where affected.

Examples:

- If a field is no longer needed, delete it everywhere instead of leaving it null or hidden.
- If a business rule changes, update the golden vector or canonical SQL first, then app code.
- If a screen reveals that the model is wrong, pause local UI polishing and correct the model.

### Test Data Is Disposable

During Phase 5R, existing local data is treated as disposable test data. Do not preserve bad shapes for compatibility with old test rows. Schema and migration changes still must be documented and consistent because the shared contract matters, but compatibility baggage is not a reason to keep incorrect concepts.

### Preserve Architecture and Simplicity

Do not use the redesign as a reason to add speculative abstractions, broad component libraries, or new dependencies. Keep the app local-first, single-user, euros-only, and Android-primary. Reuse the existing layers and helpers unless a change clearly removes duplication or fixes a real design problem.

### Derived Finance Truth Stays Derived

Balances, debts, account flow, actual income, and actual expense still come from canonical SQL views and shared queries. UI and ViewModels may present the data differently, but they must not become a second source of financial truth.

### Docs and Tests Move With Behavior

Any behavior change discovered during redesign must update the relevant docs and tests in the same slice. A completed slice should include a short manual checklist with expected results.

---

## 3. Working Loop

Logic and UI do not have to be separated cleanly. Some logic problems will only become visible while redesigning a screen.

For each page or entity, use this loop:

1. Read the relevant docs and current implementation.
2. Inspect the current logic, data shape, and tests.
3. Redesign enough of the UI/UX to expose whether the model feels right.
4. When a logic issue appears, stop local UI patching and fix the logic deeply.
5. Resume the UI on top of the corrected model.
6. Run focused tests plus the appropriate broader gates.
7. Record manual checks and any intentionally deferred follow-up.

The rule is not "logic first, UI second." The rule is: logic issues discovered at any point must be resolved deeply, not hidden locally.

---

## 4. Preferred Order

Phase 5R should proceed in dependency order, not purely visual navigation order.

1. **Global audit and shell**
   Validate navigation, top-level information architecture, global add flow, modal/sheet strategy, loading and empty states, and shared component gaps.

2. **Accounts and categories**
   These are foundational reference data. Many later screens depend on account and category identity, color, ordering, and validity.

3. **Movements and ledger**
   This is the core write surface. Validate expense, income, transfer, category, trip, tag, split entry points, refund entry points, one-time flag, and account defaults.

4. **Dashboard and analysis**
   Once ledger behavior is clean, validate the derived reading surfaces, drill-down paths, trip grouping, budget entry points, and chart language.

5. **People, splits, debts, and settlements**
   These depend on movement correctness and include the most sensitive derived debt logic.

6. **Recurring, refunds, budgets, and notifications**
   These depend on movement, category, date, actual spend, and account-flow behavior.

7. **Trips and tags**
   Revisit after ledger and analysis settle so trip/tag UX matches the final app language and still respects scope rules.

8. **Settings, sync, and read-only states**
   Finish cross-cutting and secondary surfaces after the main product surfaces stabilize.

### Advantages

- Stabilizes dependencies before dependent screens.
- Reduces rework in analysis, budgets, trips, and Windows.
- Makes logic validation easier because each slice has a clear source of truth.

### Disadvantages

- The full final look will emerge gradually, not all at once.
- Some screens may be touched twice when a later slice reveals a shared component gap.
- The user may notice model issues during UI work; the loop above allows that and requires deep fixes.

---

## 5. Slice Definition of Done

Each Phase 5R slice is done only when:

- relevant docs were read and updated if behavior changed;
- logic issues discovered in the slice were fixed deeply across affected layers;
- UI uses `docs/08-design-system.md` and `shared/design/tokens/design-tokens.json` without adding casual tokens;
- Catalan strings are externalized and fit expected mobile widths;
- no dead fields, unused UI state, or one-off helpers remain from removed concepts;
- focused tests pass, plus Android unit/build gates when the slice touches app code;
- manual checks are recorded in the final handoff.

---

## 6. Phase 5R Output

By the end of Phase 5R, Android should have:

- a validated app model and shared contract;
- a coherent visual system implemented in Compose;
- reusable components only where repetition has proven them useful;
- no temporary scaffolding that Windows would accidentally copy;
- a manual checklist for the main app flows.
