# Handoff — P5R-3 Movement form: 4-type expense cascade

**Created:** 2026-06-27
**Scope:** P5R-3 (movements + shared + external + paid-by-other).
**Read first:** `docs/15-android-redesign-validation.md` §4 (slice 3), `docs/16-android-audit-findings.md` (IDs cited below as `[#XX]`).

This is the implementation contract for the movement form. Logic and UI change together; the 4-type model is the spine.

---

## 1. The 4-type model (the spine)

Every expense is exactly one of:

| Type | Name | Who paid? | Money leaves account? | Storage | Audit |
|---|---|---|---|---|---|
| 1 | **Personal** | User | Yes | `movements` only | — |
| 2 | **Shared** | User, split among participants | Yes | `movements` + `splits` (payer_person_id NULL, movement_id set) | O1, M6 |
| 3 | **For another** ("Per a un altre") | User, on behalf of one person who owes 100% | Yes | `movements` + `splits` (user line=0, one person line=total) | — |
| 4 | **Debt** ("Deute") | Someone else; user owes an amount | **No** | `splits` only (payer_person_id set, movement_id NULL) | C5, F2, O5 |

Types 1–3 share one write path (`MovementRepository.create`/`update`); type 3 is a UX shortcut for type 2 with user=0/one-person=total (no split editor). Type 4 is the genuinely different write path (`SplitRepository`).

## 2. Locked UI decisions

- **Cascade**, not a 4-card picker:
  - Level 1 "Qui ha pagat?" → **Jo** / **Una altra persona**.
  - If Jo: Level 2 "Per a qui?" → **Només per a mi** / **Compartida** / **Per a un altre**.
  - If Una altra persona: type 4 form (no account).
- **Type 4 amount = what the user owes.** `total_amount_cents = user share` always. The spec §2.6 "group bill > user share" case is **deliberately unsupported** → F2 is WONTFIX-by-design.
- **Trip/tag allowed on type 4** → requires `splits.tag_id` (new column).

## 3. Audit IDs absorbed by this slice

`C1` (migration runner, first real migration = `splits.tag_id`), `C5` (atomic external-split edit), `F2` (resolved WONTFIX-by-design), `O1` + `M6` (`v_movement_summary`), `O4` (dead `archive` branch), `O5` (dead person line removed), `M2` (`MovementsViewModel` split), `U2` (the cascade itself), `U3` (manual checklist in §13).

---

## 4. Step 1 — Schema + migration + C1 (do first)

**4.1 New column.** `splits` gains `tag_id TEXT REFERENCES tags(id)`, nullable, with the same rule as movements (`tag_id IS NULL OR trip_id IS NOT NULL`). It lets type-4 expenses carry a trip tag like any movement.

**4.2 Migration file** `shared/migrations/002_add_splits_tag_id.sql`:
```sql
-- v1 → v2: add tag_id to splits so external (§2.6) splits can carry a trip tag.
ALTER TABLE splits ADD COLUMN tag_id TEXT REFERENCES tags(id);
UPDATE meta SET value = '2' WHERE key = 'schema_version';
```
Note: the CHECK is omitted from the ALTER (SQLite ADD COLUMN restrictions); the CHECK lives in the fresh-install schema (4.3) and is enforced by the app regardless.

**4.3 Fresh-install schema** (update both, keep byte-identical to each other):
- `shared/schema/schema.sql` — add `tag_id TEXT REFERENCES tags(id)` to the `splits` CREATE TABLE and `CHECK ( tag_id IS NULL OR trip_id IS NOT NULL )` to its constraints.
- `shared/migrations/001_initial.sql` — same change (this file is the source the SQLDelight generator reads for fresh installs; operationally it represents the *current* full schema). Keep the `meta` seed at `schema_version = '2'`.

**4.4 Migration runner (C1)** in `android/app/src/main/java/com/gestorfinances/app/data/db/`:
- The `syncSharedSqlForSqlDelight` task (`android/app/build.gradle.kts:36`) already reads `001_initial.sql` → `SharedSchema.sq`. Extend it to also emit `1.sqm` (SQLDelight migration v1→v2) from `shared/migrations/002_add_splits_tag_id.sql`, into `src/main/sqldelight/com/gestorfinances/app/data/db/1.sqm`.
- With `1.sqm` present, SQLDelight sets `GestorDatabase.Schema.version = 2` and the existing `AndroidSqliteDriver.Callback` (`DatabaseDriverFactory.kt:17`, which overrides only `onConfigure`) inherits SQLDelight's `onUpgrade` → the migration applies automatically on existing v1 DBs.
- If the `.sqm` parser rejects the `ALTER … REFERENCES` statement, fall back to overriding `Callback.onUpgrade` to `db.execSQL` the migration text read from the generated resource. The regression test (4.5) is the gate either way.

**4.5 Migration regression test** `android/app/src/test/.../data/db/MigrationTest.kt`:
- `JdbcSqliteDriver(IN_MEMORY)`; execute the **v1** `splits` CREATE (without `tag_id`); insert one split row; manually `PRAGMA user_version = 1`.
- Call `GestorDatabase.Schema.migrate(driver, 1, 2)`.
- Assert `PRAGMA table_info(splits)` now lists `tag_id`; the pre-existing row has `tag_id IS NULL`; `meta.schema_version = '2'`.

**4.6 Windows harness + parity:** `windows/.../SharedSql.cs` `ApplyBaseline` already reads `001_initial.sql` (now v2) so tests stay green. `tools/validate_sql_parity.py` and `tools/validate_shared_sql.py` need no change (they validate file inventories, which are unchanged).

**Gate:** migration test green; `:app:testDebugUnitTest` green; `dotnet test` green.

---

## 5. Step 2 — Repository layer

**5.1 `ExternalSplitDraft`** (`SplitRepository.kt:6-14`): add `tripId: String?` and `tagId: String?`. Keep `userShareCents` (= `totalAmountCents`; amount = what user owes).

**5.2 `Splits.sq insertExternalSplit`** (lines 35-62): add `:trip_id` and `:tag_id` params; drop the hardcoded `NULL`s for those two columns.

**5.3 `SplitRepository.createExternalPaidByPerson`** (lines 19-69) — **remove the dead person line (O5)**. New body writes only:
- the split row (`total_amount_cents = amount`, `payer_person_id`, `date`, `description`, `category_id`, `trip_id`, `tag_id`);
- **one** user line (`owed_amount_cents = amount`).
No `person` line — no view reads it (`v_actual_expense` / `v_person_balance` filter `participant_kind='user'` for external splits; confirmed).

**5.4 New `SplitRepository.replaceExternalSplit(id, draft, now)`** (C5): in **one** `queries.transaction {}`, archive the old split + its lines, then insert the new split (with a fresh id) + its user line. Replaces the non-atomic archive-then-insert in `MovementsViewModel` (lines 491-501).

**5.5 `MovementRepository.archive`** — delete the dead branch (O4, the lines passing the movement id as `split_id`). The preceding `archiveMovementSplit(id)` already handles movement-backed splits correctly.

**5.6 Atomicity test** (reuse the M5 harness from Tier 1): `SplitRepositoryTest.replace_external_split_rolls_back_on_failure` — make the insert fail mid-TX (e.g. invalid `payer_person_id` FK), assert the original split is still active.

---

## 6. Step 3 — Form state refactor (`MovementsViewModel.kt`)

**6.1 New enum** (replaces the two "payer" concepts `payerIsMe` + `splitEditor.paidByPersonId` + `externalPayerPersonId`):
```kotlin
enum class ExpenseKind { PERSONAL, SHARED, FOR_OTHER, DEBT }   // types 1, 2, 3, 4
```

**6.2 `MovementFormState`** (lines 923-951): replace `payerIsMe`, `externalPayerPersonId` with:
- `expenseKind: ExpenseKind? = null` (null for INCOME/TRANSFER);
- `forOtherPersonId: String? = null` (type 3's single person).
Keep `splitEditor` (type 2 only), `existingSplit`, `removeExistingSplit`.

**6.3 `normalizeForm`** (lines 734-805): for EXPENSE, derive fields from `expenseKind`:
- PERSONAL: `accountId` kept, `splitEditor=null`, `forOtherPersonId=null`.
- SHARED: `accountId` kept, `splitEditor` kept.
- FOR_OTHER: `accountId` kept, `forOtherPersonId` kept, `splitEditor=null`.
- DEBT: `accountId=null`, `forOtherPersonId=null`, `splitEditor=null` (type 4 has no account).
For INCOME/TRANSFER: `expenseKind=null`, force the existing normalization.

**6.4 `attemptSave`** (lines 399-631): four clean branches off `expenseKind`:
- DEBT → `ExternalSplitDraft` (with `tripId`/`tagId`) → `createExternalPaidByPerson` (new) or `replaceExternalSplit` (edit).
- FOR_OTHER → `MovementDraft` + a `MovementSplitWrite.Replace` built directly: one user line owed=0, one person line owed=amount. No split editor involved.
- SHARED → existing `splitEditor.toMovementSplitDraft` path.
- PERSONAL → existing no-split path.

**6.5 `toFormState` round-trip** (lines 1042-1099):
- `type == EXTERNAL_EXPENSE` → `expenseKind = DEBT`.
- movement + split where user line=0 and exactly one person line=amount → `expenseKind = FOR_OTHER`, `forOtherPersonId = that person`.
- movement + split (otherwise) → `expenseKind = SHARED`, load `splitEditor` (without the old `paidByPersonId` field).
- movement + no split → `expenseKind = PERSONAL`.

**6.6 `isDuplicate`** (lines 808-830): currently skips type 4 (`accountId == null`). Optional improvement: for DEBT, dedup on `(externalPayerPersonId, amount, date±1, name)` instead. Implement if cheap; otherwise leave and note.

---

## 7. Step 4 — UI cascade (`MovementFormSheet.kt`)

**7.1 Remove** `PayerSegmentedToggle` (lines 444-487) and the `payerIsMe`/`externalPayerPersonId` wiring in the EXPENSE row (222-262).

**7.2 Add** the cascade for EXPENSE:
- `WhoPaidSegmented` → Jo / Una altra persona (derived: `expenseKind == DEBT`).
- When Jo: `AccountSelect` + `ForWhomSegmented` → Només per a mi / Compartida / Per a un altre.
  - Compartida → `SplitEditorCard` (now editor-only; see Step 5).
  - Per a un altre → a single `FormSelect` person picker (sets `forOtherPersonId`).
- When Una altra persona: payer `FormSelect` (person) + amount (= what I owe) + date + category + trip + tag. **No account row.** Trip/tag pickers appear (trip first; tag only if trip set — reuse `FormTripTagSection`).

**7.3 Type/transfer/income rows** stay as-is.

---

## 8. Step 5 — Split editor shrinkage

**8.1 `SplitEditorState.kt`:** delete `paidByPersonId`, `withPaidByOther`, `withManualSplit`, `paidByOtherCalculation`, and the `WhoPaidSelect`-related branches in `calculation`. The editor models only: `method`, `payerParticipantId` (for rounding), `selectedPersonIds`, `exactAmounts`, `percentages`. Type 3 no longer flows through it.

**8.2 `SplitEditorCard.kt`:** delete `WhoPaidSelect` (184-218) and the `isPaidByOther` branch (80, 128-141). The card renders method + participants + amounts + reconciliation only. It is shown only for type 2.

**8.3 Strings to delete** (now-unused): `split_variant_external`, `split_variant_paid_by_other`, `split_variant_manual`, `split_paid_by_other_title`. Keep `split_payer_title`/`split_payer_user`/`split_payer_person` (used by the rounding-payer selector inside the editor).

---

## 9. Step 6 — Canonical SQL (O1, M6)

**9.1 New view** `shared/queries/v_movement_summary.sql`: centralizes `paid_by_person_name` (the type-3 detector: user line=0 ∧ one person line=amount), `user_share_cents`, `is_shared`, and the external-split UNION (type 4) — with `sl.archived_at IS NULL` on every JOIN (M6). One source instead of 4.

**9.2 `Movements.sq`:** refactor `activeMovementSummaries`, `activeMovementSummariesForAccount`, `movementById`, `activeMovementSummariesForCategory` to SELECT from `v_movement_summary`. The external UNION must now project `s.trip_id` and `s.tag_id` (populated since Step 2).

**9.3 Wire the new view** into `android/app/build.gradle.kts` `sharedViewFiles`, `windows/.../SharedSql.cs` `ViewFiles`, and `tools/validate_shared_sql.py` `VIEW_FILES` (parity — three lists must agree).

---

## 10. Step 7 — Strings (add)

New Catalan strings in `strings.xml`:
- `movement_whopaid_title` = "Qui ha pagat?"
- `movement_whopaid_me` = "Jo"
- `movement_whopaid_other` = "Una altra persona"
- `movement_forwhom_title` = "Per a qui?"
- `movement_forwhom_personal` = "Només per a mi"
- `movement_forwhom_shared` = "Compartida"
- `movement_forwhom_other` = "Per a un altre"
- `movement_debt_payer` = "Persona que ha pagat"
- `movement_debt_amount_help` = "L'import que li deus." (helper under the amount field when type 4)

Reuse existing `movement_field_shared`, `split_*` method/reconcile labels. Delete the §8.3 list.

---

## 11. Step 8 — Tests

- **Migration** (Step 4.5).
- **Atomicity** (Step 5.6) — `replaceExternalSplit`.
- **Round-trip** `MovementFormStateRoundTripTest`: for each of the 4 kinds, build form → save → reload via `toFormState` → assert same `expenseKind` + key fields. Covers the type-3/type-4 detection on load.
- **Repository** `SplitRepositoryTest`: `createExternalPaidByPerson` writes exactly one user line (no person line); carries `trip_id`/`tag_id`.
- Golden suite stays green; `v_movement_summary` preserves the existing `paid_by_person_name` semantics (add a fixed-data assertion if not already covered).

---

## 12. Step 9 — Doc updates (docs move with behavior)

- `docs/04-data-model.md` §3 (`splits`) + §4: add `tag_id` column + CHECK; record that type-4 `total = user share` (the §2.6 group-bill case is out of scope v1).
- `docs/16-android-audit-findings.md`: mark **F2 = WONTFIX-by-design** (type 4 simplified); confirm **O5** removed; record C1 built here.
- `docs/06-roadmap.md` P5R-3 line: note the `splits.tag_id` migration (first real migration, C1).
- `docs/15-android-redesign-validation.md`: add §10 P5R-3 Manual Checklist (below).
- Delete `docs/handoff-p5r2.md` (replaced by this file).

---

## 13. P5R-3 Manual Checklist (U3) — draft into `docs/15` §10

| # | Step | Expected |
|---|------|----------|
| 1 | New expense, "Jo" → "Només per a mi", save | Movement list shows it; balance drops by amount; no split row |
| 2 | "Jo" → "Compartida", add Anna, equal split, save | Movement + split; Anna's balance = her share; your share in analysis |
| 3 | "Jo" → "Per a un altre", pick Anna, save | Movement + split; **Anna owes the full amount**; your actual expense = 0; balance drops by full amount |
| 4 | "Una altra persona" → pick Anna, amount 30€, trip "Berlín", tag "Vols", save | No movement; **you owe Anna 30€**; appears in Berlín trip analysis with the Vols tag |
| 5 | Edit the type-3 expense, change amount | Updates; Anna's debt updates; id stable (movement update in place) |
| 6 | Edit the type-4 expense, change amount | Updates; your debt to Anna updates; **atomic** (kill mid-save leaves old row intact) |
| 7 | Duplicate a type-1 expense (same acct/amount/date/name) | Warning banner; override saves |
| 8 | Cold-launch on an existing v1 DB | App upgrades to v2 (`splits.tag_id` added); no data loss; `meta.schema_version=2` |

---

## 14. Definition of done

- All 4 types creatable, editable, and correctly analyzed via the cascade; the two-door trap is gone.
- Audit IDs C1, C5, O1, O4, O5, M2, M6, U2, U3 resolved; F2 WONTFIX-by-design recorded.
- `:app:testDebugUnitTest` + `dotnet test` + Python validators green; migration regression + atomicity + round-trip tests green.
- `spec-guardian` + `simplicity-guardian` clean; the split editor is editor-only (no internal "who paid"); no dangling strings.
- Manual checklist (§13) passes; docs 04/15/16 + roadmap consistent.
