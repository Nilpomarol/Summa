# Personal Finance App — Data Model

> Companion to `00-Full_Spec.md` (*what*), `02-synchronization.md` (sync), and `03-architecture.md` (*how*). This document defines the **SQLite schema** — the shared artifact both apps build against (arch §4). SQL is SQLite dialect; it is authored once here and consumed verbatim (SQLDelight on Android, Microsoft.Data.Sqlite on Windows).
>
> **Status:** Working draft, complete and reviewed against the spec. Covers conventions, all entity tables, and the core canonical SQL; the §2.6 question (§5) is decided. Remaining build artifacts (full analysis-query set, migrations, golden tests) are listed in §8.

---

## 1. Conventions

Applied uniformly across every table.

- **Identifiers.** `id TEXT PRIMARY KEY` holding a **UUID** (v4; v7 optional for index locality). Chosen over `INTEGER` rowids for stable, merge-safe identity (export/import, §5.6 of the spec) and to avoid any cross-context collisions. The performance cost is negligible at this data scale (spec §7.3).
- **Money.** Integer **euro cents**, always suffixed `_cents` (spec §2.8). Never `REAL`/decimal. Movement amounts are stored **positive**; direction comes from `type` (spec §3.0).
- **Calendar dates.** Columns named `date` (the *value date*) are `TEXT` in `'YYYY-MM-DD'` — a local calendar date, no time, no zone (arch §2.1).
- **Instants.** Audit timestamps end in `_at` and are `TEXT` ISO-8601 **UTC** (`'YYYY-MM-DDTHH:MM:SSZ'`).
- **Soft-delete.** `archived_at TEXT NULL` is the universal soft-delete marker: **active ⇔ `archived_at IS NULL`** (it doubles as the archive timestamp, replacing a separate boolean — spec §4.7).
- **Booleans / enums.** Booleans as `INTEGER` `CHECK (col IN (0,1))`. Enums as `TEXT` with a `CHECK (col IN (...))` so the schema is self-documenting and self-validating.
- **Shared mixin (every table).** `id`, `created_at`, `updated_at`, `archived_at` (spec §3 mixin). Listed explicitly in each DDL below rather than abstracted, since SQLite has no table inheritance.
- **Foreign keys.** `PRAGMA foreign_keys = ON`. References to *entities* use `ON DELETE RESTRICT` (we archive, not delete); *satellites* of a movement use `ON DELETE CASCADE`.
- **Connection PRAGMAs.** `journal_mode = WAL` (assumed by the snapshot mechanics, arch §2.2), `foreign_keys = ON`, `busy_timeout` set by each app.

---

## 2. `movements` — the unified ledger

The base ledger record for all five types (spec §2.1, §3.0). Type-specific fields hang off it as nullable columns, with `CHECK` constraints enforcing that each type carries exactly the fields it should.

```sql
CREATE TABLE movements (
    id                   TEXT    PRIMARY KEY,                 -- UUID
    type                 TEXT    NOT NULL CHECK (type IN ('expense','income','transfer','settlement','refund')),
    amount_cents         INTEGER NOT NULL CHECK (amount_cents > 0),       -- always positive; direction implied by type
    date                 TEXT    NOT NULL,                    -- 'YYYY-MM-DD' value date

    account_id           TEXT    NOT NULL REFERENCES accounts(id),  -- origin account
    dest_account_id      TEXT    REFERENCES accounts(id),           -- transfers only (destination)

    name                 TEXT,                                 -- free-text description
    payee                TEXT,
    notes                TEXT,
    is_one_time          INTEGER NOT NULL DEFAULT 0 CHECK (is_one_time IN (0,1)),  -- extraordinary/one-off purchase; orthogonal to category nature; analysis can exclude

    category_id          TEXT    REFERENCES categories(id),    -- expense/income/refund only
    tag_id               TEXT    REFERENCES tags(id),          -- only meaningful when trip_id set
    trip_id              TEXT    REFERENCES trips(id),
    template_id          TEXT    REFERENCES templates(id),     -- set if materialized from a template
    import_batch_id      TEXT    REFERENCES import_batches(id),

    person_id            TEXT    REFERENCES people(id),        -- settlements only
    settlement_direction TEXT    CHECK (settlement_direction IN ('person_to_user','user_to_person')),

    refunds_expense_id   TEXT    REFERENCES movements(id),     -- refunds only (self-reference)
    actual_refund_cents  INTEGER CHECK (actual_refund_cents IS NULL OR actual_refund_cents >= 0),
                         -- refunds only; NULL => actual adjustment equals amount_cents

    created_at           TEXT    NOT NULL,
    updated_at           TEXT    NOT NULL,
    archived_at          TEXT,

    -- type ⇔ field integrity ((bool) = (bool) enforces "iff" in SQLite)
    CHECK ( (type = 'transfer')   = (dest_account_id IS NOT NULL) ),
    CHECK ( dest_account_id IS NULL OR dest_account_id <> account_id ),
    CHECK ( category_id IS NULL OR type IN ('expense','income','refund') ),
    CHECK ( tag_id IS NULL OR trip_id IS NOT NULL ),
    CHECK ( (type = 'settlement') = (person_id IS NOT NULL) ),
    CHECK ( (type = 'settlement') = (settlement_direction IS NOT NULL) ),
    CHECK ( (type = 'refund')     = (refunds_expense_id IS NOT NULL) ),
    CHECK ( actual_refund_cents IS NULL OR type = 'refund' ),
    CHECK ( actual_refund_cents IS NULL OR actual_refund_cents <= amount_cents ),
    CHECK ( is_one_time = 0 OR type = 'expense' )
);

CREATE INDEX idx_movements_date          ON movements(date);
CREATE INDEX idx_movements_account_date  ON movements(account_id, date);
CREATE INDEX idx_movements_dest_account  ON movements(dest_account_id) WHERE dest_account_id IS NOT NULL;
CREATE INDEX idx_movements_category_date ON movements(category_id, date) WHERE category_id IS NOT NULL;
CREATE INDEX idx_movements_trip          ON movements(trip_id)     WHERE trip_id IS NOT NULL;
CREATE INDEX idx_movements_person        ON movements(person_id)   WHERE person_id IS NOT NULL;
CREATE INDEX idx_movements_template      ON movements(template_id) WHERE template_id IS NOT NULL;
CREATE INDEX idx_movements_refunds       ON movements(refunds_expense_id) WHERE refunds_expense_id IS NOT NULL;
CREATE INDEX idx_movements_import_batch  ON movements(import_batch_id)    WHERE import_batch_id IS NOT NULL;
```

### Resolved physical decisions (deferred from the spec)

- **Transfer = one row** (spec §3.5): a single movement with `account_id` (origin) + `dest_account_id` (destination), not two mirrored rows. Flow contributes `−amount_cents` to origin and `+amount_cents` to destination (handled in the canonical flow SQL, §6). Enforced: `dest_account_id` is non-null **iff** `type='transfer'`, and must differ from the origin.
- **`is_shared` is computed, not stored** (spec §3.0): a movement is shared **iff** a `splits` row references it — `EXISTS (SELECT 1 FROM splits WHERE movement_id = movements.id)`. No stored column (SQLite generated columns can't reference other tables, and a maintained flag would risk drift). Exposed via a view in §6.
- **Pending recurring occurrences are virtual prompts, not `movements` rows** (spec §3.10, §4.3): `movements` is a confirmed ledger only. Overdue prompts are generated from `templates.next_due_date`; confirm creates a movement with `template_id`, skip advances the template cursor without creating anything. This keeps variable-amount drafts out of the ledger and avoids loosening `amount_cents NOT NULL`.
- **Settlement direction** is an explicit column (`person_to_user` inflow / `user_to_person` outflow), non-null iff `type='settlement'` (spec §3.9).
- **Refund linkage** is the self-reference `refunds_expense_id`, non-null iff `type='refund'`. It must point at an `expense` row; SQLite can't enforce the *target's* type cross-row, so the app enforces it and a `CHECK` guarantees presence. `amount_cents` is the cash inflow for account flow; `actual_refund_cents` is an optional override for the spending-analysis reduction when it differs from cash flow (notably shared-expense refunds). If null, actual uses `amount_cents`.

---

## 3. `splits` & `split_lines` — shared expenses

How a shared expense is divided (spec §3.7). This is also where the spec's two special cases are resolved into one mechanism (spec §2.5, §2.6, §3.16).

```sql
CREATE TABLE splits (
    id                 TEXT    PRIMARY KEY,
    movement_id        TEXT    REFERENCES movements(id) ON DELETE CASCADE,  -- NULL ⇒ §2.6 (no ledger movement)
    payer_person_id    TEXT    REFERENCES people(id),                       -- NULL ⇒ the user fronted it
    entry_method       TEXT    NOT NULL CHECK (entry_method IN ('equal','exact','percentage')),
    total_amount_cents INTEGER CHECK (total_amount_cents IS NULL OR total_amount_cents > 0),
                         -- required only when movement_id IS NULL (§2.6); v1 total = user share
    date               TEXT,                                                -- required only when movement_id IS NULL
    description        TEXT,                                                -- §2.6 only; movement-backed splits use movements.name/notes
    category_id        TEXT    REFERENCES categories(id),                   -- §2.6 only; movement-backed splits use movements.category_id
    trip_id            TEXT    REFERENCES trips(id),                        -- §2.6 only; movement-backed splits use movements.trip_id
    tag_id             TEXT    REFERENCES tags(id),                         -- §2.6 only; one trip tag max, only when trip_id set (mirrors movements)

    created_at         TEXT    NOT NULL,
    updated_at         TEXT    NOT NULL,
    archived_at        TEXT,

    -- exactly two valid configurations:
    --   user fronted   : payer_person_id IS NULL  AND movement_id IS NOT NULL  (normal, incl. "paid by other" §2.5)
    --                    movement owns date/category/trip/description/total; tag_id is NULL
    --   person paid ext: payer_person_id IS NOT NULL AND movement_id IS NULL   (§2.6, no user-account movement)
    --                    split owns date/category/trip/description/total (and tag_id, when trip_id set)
    CHECK (
        (payer_person_id IS NULL AND movement_id IS NOT NULL
         AND total_amount_cents IS NULL AND date IS NULL
         AND description IS NULL AND category_id IS NULL AND trip_id IS NULL
         AND tag_id IS NULL)
        OR
        (payer_person_id IS NOT NULL AND movement_id IS NULL
         AND total_amount_cents IS NOT NULL AND date IS NOT NULL)
    ),
    CHECK ( tag_id IS NULL OR trip_id IS NOT NULL )   -- tag only meaningful when trip set (spec §3.13)
);
CREATE UNIQUE INDEX idx_splits_movement ON splits(movement_id) WHERE movement_id IS NOT NULL;  -- one split per movement
CREATE INDEX idx_splits_payer ON splits(payer_person_id) WHERE payer_person_id IS NOT NULL;

CREATE TABLE split_lines (
    id                TEXT    PRIMARY KEY,
    split_id          TEXT    NOT NULL REFERENCES splits(id) ON DELETE CASCADE,
    participant_kind  TEXT    NOT NULL CHECK (participant_kind IN ('user','person')),
    person_id         TEXT    REFERENCES people(id),          -- NULL iff participant_kind='user'
    owed_amount_cents INTEGER NOT NULL CHECK (owed_amount_cents >= 0),
    owed_percent      REAL,                                   -- optional, retained for display only

    created_at        TEXT    NOT NULL,
    updated_at        TEXT    NOT NULL,
    archived_at       TEXT,

    CHECK ( (participant_kind = 'user') = (person_id IS NULL) )
);
CREATE INDEX idx_split_lines_split  ON split_lines(split_id);
CREATE INDEX idx_split_lines_person ON split_lines(person_id) WHERE person_id IS NOT NULL;
CREATE UNIQUE INDEX idx_split_lines_one_user
    ON split_lines(split_id)
    WHERE participant_kind = 'user' AND archived_at IS NULL;
CREATE UNIQUE INDEX idx_split_lines_one_person
    ON split_lines(split_id, person_id)
    WHERE participant_kind = 'person' AND archived_at IS NULL;
```

### The three configurations (canonical forms)

| Case | `movement_id` | `payer_person_id` | Lines stored | Meaning |
|---|---|---|---|---|
| **Normal shared expense** (user paid) | set | NULL | user's share + each other person's share (Σ = `amount_cents`) | each person owes the user their share; the user's own line feeds *actual* analysis |
| **"Paid by other"** §2.5 (through the user's account) | set | NULL | user line = 0, one person line = full | money left the user's account; that person owes it all back. UI labels it "pagat per X" when the user's line is 0 |
| **Paid by person externally** §2.6 | NULL | the payer | exactly one **user line** (`owed_amount_cents = total_amount_cents`) | no ledger movement; the user *owes* the payer their share. `total_amount_cents = user share` always — group-bill-larger-than-user-share is out of scope v1 (F2 WONTFIX). `tag_id` is supported when `trip_id` is set. |

### Reconciliation rules (app-enforced; not all are cross-row CHECKable)

- **User-fronted split** (`movement_id` set): `Σ split_lines.owed_amount_cents = movements.amount_cents`, and a **user line must exist** (it is what `actual` analysis reads). Date/category/trip/description live only on the parent movement.
- **§2.6 split** (`payer_person_id` set): exactly one user line with `owed_amount_cents = total_amount_cents` (group-bill support is out of scope v1, F2 WONTFIX). Date/category/trip/`tag_id` live on the split because there is no parent movement.
- **Participant uniqueness:** at most one active user line per split and at most one active line per person per split.
- **Equal-split rounding:** computed in integer cents; remainder cent(s) to the payer so the lines reconcile exactly (spec §3.7).
- **Only expenses are shared:** a `splits` row may reference only an `expense` movement (spec §3.0 "only expense may be shared"; §3.5 "transfers are never shared"). SQLite can't cross-row CHECK the target's type, so the app enforces it — the same pattern as the refund-target-type rule (§2).

---

## 4. Refunds, transfers, settlements (recap)

These need **no extra tables** — they are configurations of `movements` (§2):

- **Refund:** `type='refund'`, `refunds_expense_id` → the refunded expense, `amount_cents` = cash inflow, own `date`/`category_id`. `actual_refund_cents` is optional and only needed when the analysis reduction differs from the cash inflow (shared-expense refunds); null means use `amount_cents`. Reconciliation (app): `Σ refunds.amount_cents ≤ expense.amount_cents` — warn-and-allow over-refund (spec §3.3b); and for a shared expense, `Σ actual_refund_cents` must not exceed the user's own split share.
- **Transfer:** `type='transfer'`, `account_id` + `dest_account_id`; never categorized or shared.
- **Settlement:** `type='settlement'`, `person_id` + `settlement_direction`, `account_id` = the account that sent/received; never categorized, never in `actual` (spec §3.9).

---

## 5. Friend-paid shares and *actual* spending — **[DECIDED — A]**

A §2.6 split (a friend paid externally, you owe your share) has **no movement**, so the user's line contributes to `actual` spending directly from the split. This is now part of the full spec (§2.6, §4.2): it avoids understating real consumption and serves the deep-spending-analysis goal. Consequence: `actual` draws from two sources (expense movements + §2.6 user lines) and intentionally diverges from pure account flow for these items. Implemented in the `v_actual_expense` view (§7). It does **not** count as income and never double-counts (the later settlement is excluded from analysis, spec §3.9).

> A no-movement split therefore carries its own `category_id`, `trip_id`, and `date` (§3) so a friend-paid share can be categorized and trip-grouped like any expense. Movement-backed splits leave those fields null and use the parent movement.

---

## 6. Entity tables

> DDL below is grouped thematically, not in execution order. A migration creates referenced tables first (accounts, categories, people, trips, tags → templates, budgets, rules, import_batches → movements, splits). SQLite tolerates forward FK references at `CREATE` time; enforcement is per-row with `foreign_keys = ON`.

```sql
CREATE TABLE accounts (
    id                          TEXT    PRIMARY KEY,
    name                        TEXT    NOT NULL,
    starting_balance_cents      INTEGER NOT NULL DEFAULT 0,   -- may be negative (overdraft)
    type                        TEXT    NOT NULL CHECK (type IN ('bank','cash','savings','investment','other')),
    icon                        TEXT,
    color                       TEXT,
    is_default                  INTEGER NOT NULL DEFAULT 0 CHECK (is_default IN (0,1)),
    display_order               INTEGER NOT NULL DEFAULT 0,
    low_balance_threshold_cents INTEGER,                      -- optional; triggers low-balance alert (spec §5.3)
    created_at  TEXT NOT NULL, updated_at TEXT NOT NULL, archived_at TEXT
);
-- at most one global default account:
CREATE UNIQUE INDEX idx_accounts_one_default ON accounts(is_default) WHERE is_default = 1;
-- `current_balance` is DERIVED, never stored (see v_account_balance, §7).

CREATE TABLE categories (
    id            TEXT    PRIMARY KEY,
    name          TEXT    NOT NULL,
    kind          TEXT    NOT NULL CHECK (kind IN ('expense','income','both')),
    nature        TEXT    NOT NULL CHECK (nature IN ('fixed','variable')),   -- movements inherit nature via this category
    parent_id     TEXT    REFERENCES categories(id),                         -- two-level; a parent has parent_id NULL (depth enforced in app)
    icon TEXT, color TEXT, display_order INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL, updated_at TEXT NOT NULL, archived_at TEXT
);
CREATE INDEX idx_categories_parent ON categories(parent_id) WHERE parent_id IS NOT NULL;
-- "Sense categoria" is not a row: it is the category_id IS NULL bucket in queries (spec §3.2).

CREATE TABLE people (
    id    TEXT PRIMARY KEY,
    name  TEXT NOT NULL,
    avatar TEXT, color TEXT, notes TEXT,
    created_at TEXT NOT NULL, updated_at TEXT NOT NULL, archived_at TEXT
);
-- `balance` is DERIVED (see v_person_balance, §7).

CREATE TABLE trips (
    id                 TEXT PRIMARY KEY,
    name               TEXT NOT NULL,
    type               TEXT NOT NULL CHECK (type IN ('trip','celebration','other')),
    status             TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('planned','active','finished')),  -- informational
    start_date         TEXT,                         -- 'YYYY-MM-DD'
    end_date           TEXT,                         -- NULL ⇒ ongoing
    icon TEXT, color TEXT, notes TEXT,
    default_account_id TEXT REFERENCES accounts(id),
    created_at TEXT NOT NULL, updated_at TEXT NOT NULL, archived_at TEXT
);
-- A trip budget is a `budgets` row with scope='trip' (budgets are a separate entity, spec §8).

CREATE TABLE tags (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    icon TEXT, color TEXT,
    trip_id     TEXT REFERENCES trips(id),           -- NULL ⇒ global (reusable); set ⇒ trip-local (spec §3.13)
    category_id TEXT REFERENCES categories(id),      -- optional "subcategory" association: inherit icon/color, roll up for cross-trip analysis
    trip_type   TEXT,                                -- optional event-type scope (values match TripType.dbValue: 'trip'/'celebration'/'other')
    created_at TEXT NOT NULL, updated_at TEXT NOT NULL, archived_at TEXT,

    CHECK ( trip_id IS NULL OR trip_type IS NULL )   -- a tag is global (both NULL), OR type-scoped, OR trip-specific — never two at once
);
CREATE INDEX idx_tags_trip ON tags(trip_id) WHERE trip_id IS NOT NULL;
-- schema_version=3: migration 003_add_tag_category_and_type.sql adds category_id/trip_type to tags.
-- The CHECK above only applies to fresh installs — SQLite's ADD COLUMN cannot introduce a
-- multi-column CHECK for existing (upgraded) databases, so the app layer (TagsViewModel) mirrors
-- this rule for upgraders, matching how migration 002 added splits.tag_id without retrofitting a
-- matching CHECK either.

-- Recurring pattern detection (spec §3.10 [DECIDED] addendum) proposes rows here through the same
-- create/update path as manual template entry — there is no dedicated detection schema; a
-- confirmed detected candidate is indistinguishable from a manually created template. Confirming a
-- candidate also retroactively sets template_id on its source movements (movements.template_id
-- below), and deleting a template clears it back to NULL on all of them — both via plain bulk
-- UPDATEs, no new columns.
CREATE TABLE templates (                              -- recurring movement definitions (spec §3.10)
    id              TEXT    PRIMARY KEY,
    -- pre-fill payload (mirrors movement fields)
    type            TEXT    NOT NULL CHECK (type IN ('expense','income','transfer')),  -- no recurring settlements/refunds
    amount_cents    INTEGER CHECK (amount_cents IS NULL OR amount_cents > 0),          -- NULL ⇒ variable amount
    account_id      TEXT    NOT NULL REFERENCES accounts(id),
    dest_account_id TEXT    REFERENCES accounts(id),
    category_id     TEXT    REFERENCES categories(id),
    tag_id          TEXT    REFERENCES tags(id),
    trip_id         TEXT    REFERENCES trips(id),
    name TEXT, payee TEXT, notes TEXT,
    split_config    TEXT,                              -- JSON: carried-forward split shares (pre-fill only); NULL if not shared
    -- schedule
    frequency       TEXT    NOT NULL CHECK (frequency IN ('weekly','fortnightly','monthly','yearly','custom')),
    interval_count  INTEGER,                           -- for 'custom' (every N units)
    custom_unit     TEXT    CHECK (custom_unit IN ('days','weeks','months','years')),
    day_of_month    INTEGER CHECK (day_of_month BETWEEN 1 AND 31),  -- monthly/yearly anchor; clamped to month end at materialization
    weekday         INTEGER CHECK (weekday BETWEEN 0 AND 6),        -- weekly/fortnightly anchor
    next_due_date   TEXT    NOT NULL,                  -- GENERATION CURSOR: next occurrence not yet materialized
    -- flexibility & behavior
    amount_is_variable     INTEGER NOT NULL DEFAULT 0 CHECK (amount_is_variable IN (0,1)),
    amount_flex_cents      INTEGER,                    -- optional ± margin
    date_flex_days         INTEGER,                    -- optional ± days
    lead_notification_days INTEGER,                    -- per-template; falls back to global default
    status          TEXT    NOT NULL DEFAULT 'active' CHECK (status IN ('active','paused','ended')),
    created_at TEXT NOT NULL, updated_at TEXT NOT NULL, archived_at TEXT,
    CHECK ( (type = 'transfer') = (dest_account_id IS NOT NULL) ),
    CHECK ( category_id IS NULL OR type IN ('expense','income') ),
    CHECK ( tag_id IS NULL OR trip_id IS NOT NULL ),
    CHECK ( amount_is_variable = 1 OR amount_cents IS NOT NULL ),
    CHECK ( amount_flex_cents IS NULL OR amount_flex_cents >= 0 ),
    CHECK ( date_flex_days IS NULL OR date_flex_days >= 0 ),
    CHECK ( lead_notification_days IS NULL OR lead_notification_days >= 0 ),
    CHECK (
        (frequency = 'custom' AND interval_count IS NOT NULL AND custom_unit IS NOT NULL)
        OR
        (frequency <> 'custom' AND interval_count IS NULL AND custom_unit IS NULL)
    ),
    CHECK ( interval_count IS NULL OR interval_count > 0 )
);
CREATE INDEX idx_templates_next_due ON templates(next_due_date) WHERE status = 'active';

CREATE TABLE budgets (                                 -- spec §3.14
    id                      TEXT    PRIMARY KEY,
    scope                   TEXT    NOT NULL CHECK (scope IN ('category','overall_month','trip')),
    category_id             TEXT    REFERENCES categories(id),
    trip_id                 TEXT    REFERENCES trips(id),
    period                  TEXT    NOT NULL CHECK (period IN ('monthly','one_off')),
    limit_amount_cents      INTEGER NOT NULL CHECK (limit_amount_cents > 0),
    start_date              TEXT,                       -- anchors one_off / recurrence
    alert_threshold_percent INTEGER CHECK (alert_threshold_percent BETWEEN 1 AND 100),  -- budget alert (spec §5.3)
    created_at TEXT NOT NULL, updated_at TEXT NOT NULL, archived_at TEXT,
    CHECK (
        (scope='category'      AND category_id IS NOT NULL AND trip_id IS NULL) OR
        (scope='trip'          AND trip_id     IS NOT NULL AND category_id IS NULL) OR
        (scope='overall_month' AND category_id IS NULL     AND trip_id IS NULL)
    )
);

CREATE TABLE auto_cat_rules (                          -- spec §3.15
    id                 TEXT    PRIMARY KEY,
    name               TEXT    NOT NULL,
    priority           INTEGER NOT NULL,
    conditions         TEXT    NOT NULL,                -- JSON: matchers (text / amount range / date / account)
    action_category_id TEXT    REFERENCES categories(id),
    action_trip_id     TEXT    REFERENCES trips(id),
    source             TEXT    NOT NULL CHECK (source IN ('user','system_learned')),
    active             INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0,1)),
    created_at TEXT NOT NULL, updated_at TEXT NOT NULL, archived_at TEXT
);
-- Tie-break: higher priority wins; on equal priority the NEWER rule wins (spec §3.15, §4.4):
CREATE INDEX idx_rules_match ON auto_cat_rules(priority DESC, created_at DESC) WHERE active = 1;

CREATE TABLE import_batches (                          -- spec §4.6
    id          TEXT    PRIMARY KEY,
    source_file TEXT,
    account_id  TEXT    NOT NULL REFERENCES accounts(id),   -- destination account
    row_count   INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL, updated_at TEXT NOT NULL, archived_at TEXT
);
-- Rollback a batch = delete/archive movements WHERE import_batch_id = ?.

CREATE TABLE meta (                                    -- key/value; no mixin
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
-- seed: ('schema_version','5'), ('snapshot_version','0'); also app settings (theme, default lead days, ...).
-- schema_version=2: migration 002_add_splits_tag_id.sql adds tag_id to splits (C1).
-- schema_version=3: migration 003_add_tag_category_and_type.sql adds category_id/trip_type to tags.
-- schema_version=4: migration 004_add_v_trip_actual_total_view.sql creates v_trip_actual_total for
--   upgraders (fresh installs already get it via shared/queries/v_trip_actual_total.sql), and
--   recreates v_actual_expense with its current tag_id-per-branch shape — that view has existed
--   since before schema_version existed (never embedded in any earlier migration), so any
--   database ever fresh-created before the tag_id fix needs it recreated on upgrade too.
-- schema_version=5: migration 005_fix_v_movement_summary_external_amount.sql recreates
--   v_movement_summary so the external_expense branch's amount_cents sources from
--   splits.total_amount_cents (the split's total cost) instead of the user's owed share,
--   matching every other row type in the view (§7.7).
```

---

## 7. Canonical SQL (derivation) library

The heart of the shared layer (arch §4.1): authored once here, run verbatim by both apps. `movements` contains only confirmed ledger rows, so all ledger/analysis derivations filter out archived rows.

### 7.1 `is_shared` helper

```sql
CREATE VIEW v_movement_shared AS
SELECT m.id AS movement_id,
       EXISTS (SELECT 1 FROM splits s WHERE s.movement_id = m.id AND s.archived_at IS NULL) AS is_shared
FROM movements m;
```

### 7.2 Account flow & balance (everything counts; transfers are dual-signed)

```sql
CREATE VIEW v_account_flow AS
-- single-account legs + transfer ORIGIN leg
SELECT account_id, date, id AS movement_id,
       CASE type
            WHEN 'income'     THEN  amount_cents
            WHEN 'refund'     THEN  amount_cents
            WHEN 'expense'    THEN -amount_cents
            WHEN 'transfer'   THEN -amount_cents
            WHEN 'settlement' THEN CASE settlement_direction
                                        WHEN 'person_to_user' THEN  amount_cents
                                        WHEN 'user_to_person' THEN -amount_cents END
       END AS delta_cents
FROM movements
WHERE archived_at IS NULL
UNION ALL
-- transfer DESTINATION leg
SELECT dest_account_id, date, id, amount_cents
FROM movements
WHERE type = 'transfer' AND archived_at IS NULL;

CREATE VIEW v_account_balance AS
SELECT a.id AS account_id,
       a.starting_balance_cents
         + COALESCE((SELECT SUM(f.delta_cents) FROM v_account_flow f WHERE f.account_id = a.id), 0)
       AS current_balance_cents
FROM accounts a;
-- Net worth = SELECT SUM(current_balance_cents) FROM v_account_balance (over non-archived accounts).
```

### 7.3 Actual spent / earned (only the user's own; net of refunds; incl. §2.6 — decision A)

```sql
CREATE VIEW v_actual_expense AS
-- (a) own expense movements: full amount if not shared, else the user's own split line
SELECT m.id AS source_id, m.date, m.category_id, m.trip_id, m.tag_id,
       CASE WHEN s.id IS NULL THEN m.amount_cents
            ELSE COALESCE((SELECT sl.owed_amount_cents FROM split_lines sl
                           WHERE sl.split_id = s.id AND sl.participant_kind = 'user'
                             AND sl.archived_at IS NULL), 0)
       END AS amount_cents,
       m.is_one_time
FROM movements m
LEFT JOIN splits s ON s.movement_id = m.id AND s.archived_at IS NULL
WHERE m.type = 'expense' AND m.archived_at IS NULL
UNION ALL
-- (b) refunds: negative contribution in the refund's own category & period (spec §3.3b, §4.2);
--     inherit the refunded expense's tag_id and one-time flag so excluding one-time drops the refund too
SELECT m.id, m.date, m.category_id, m.trip_id,
       (SELECT e.tag_id FROM movements e WHERE e.id = m.refunds_expense_id),
       -COALESCE(m.actual_refund_cents, m.amount_cents),
       COALESCE((SELECT e.is_one_time FROM movements e WHERE e.id = m.refunds_expense_id), 0)
FROM movements m
WHERE m.type = 'refund' AND m.archived_at IS NULL
UNION ALL
-- (c) §2.6 friend-paid shares (no movement): the user's own line counts (decision A, §5); not one-time-flaggable in v1
SELECT s.id, s.date, s.category_id, s.trip_id, s.tag_id,
       COALESCE((SELECT sl.owed_amount_cents FROM split_lines sl
                 WHERE sl.split_id = s.id AND sl.participant_kind = 'user'
                   AND sl.archived_at IS NULL), 0),
       0
FROM splits s
WHERE s.payer_person_id IS NOT NULL AND s.movement_id IS NULL AND s.archived_at IS NULL;
```

`v_actual_expense` exposes `tag_id` directly on every branch (movements' own `tag_id`, refunds inheriting the refunded expense's `tag_id`, and external splits' own `tag_id` — added alongside the `tags.category_id`/`trip_type` columns below) so trip/tag analysis can group on it without re-joining `movements`, which would silently drop the tag on external splits (their `source_id` is a `splits.id`, not a `movements.id`).

```sql

CREATE VIEW v_actual_income AS                          -- settlements and refunds are NOT income
SELECT m.id AS source_id, m.date, m.category_id, m.trip_id, m.amount_cents
FROM movements m
WHERE m.type = 'income' AND m.archived_at IS NULL;
```

### 7.4 Per-person debt balance (spec §4.1)

```sql
CREATE VIEW v_person_balance AS
SELECT p.id AS person_id,
   COALESCE((SELECT SUM(sl.owed_amount_cents)                         -- they owe the user (user-fronted splits)
             FROM split_lines sl
             JOIN splits s    ON s.id = sl.split_id
             JOIN movements m ON m.id = s.movement_id                 -- exclude archived parent movements
             WHERE sl.person_id = p.id AND sl.participant_kind = 'person'
               AND s.payer_person_id IS NULL
               AND m.archived_at IS NULL
               AND s.archived_at IS NULL AND sl.archived_at IS NULL), 0)
 - COALESCE((SELECT SUM(sl.owed_amount_cents)                         -- the user owes them (§2.6: this person paid)
             FROM split_lines sl JOIN splits s ON s.id = sl.split_id
             WHERE s.payer_person_id = p.id AND sl.participant_kind = 'user'
               AND s.archived_at IS NULL AND sl.archived_at IS NULL), 0)
 - COALESCE((SELECT SUM(m.amount_cents) FROM movements m              -- settlements person → user
             WHERE m.type='settlement' AND m.person_id=p.id AND m.settlement_direction='person_to_user'
               AND m.archived_at IS NULL), 0)
 + COALESCE((SELECT SUM(m.amount_cents) FROM movements m              -- settlements user → person
             WHERE m.type='settlement' AND m.person_id=p.id AND m.settlement_direction='user_to_person'
               AND m.archived_at IS NULL), 0)
 AS balance_cents       -- > 0: person owes the user;  < 0: the user owes the person
FROM people p;
```

### 7.5 Analysis breakdowns (representative; `:from`/`:to` are 'YYYY-MM-DD' params)

```sql
-- Actual spend by category for a period (uncategorized rolls into the NULL "Sense categoria" bucket)
SELECT category_id, SUM(amount_cents) AS spent_cents
FROM v_actual_expense
WHERE date >= :from AND date < :to
  AND (
      :one_time_mode = 'include'
      OR (:one_time_mode = 'exclude' AND is_one_time = 0)
      OR (:one_time_mode = 'only' AND is_one_time = 1)
  )
GROUP BY category_id;

-- Trips-as-blocks (spec §3.12): trip movements collapse into one line, the rest by category
SELECT CASE WHEN trip_id IS NOT NULL THEN 'trip:' || trip_id ELSE 'cat:' || COALESCE(category_id,'none') END AS bucket,
       SUM(amount_cents) AS spent_cents
FROM v_actual_expense
WHERE date >= :from AND date < :to
GROUP BY bucket;
```

Fixed-vs-variable joins `v_actual_expense` → `categories.nature`; one-time (extraordinary) spend is the `is_one_time` flag carried on `v_actual_expense` — shown as its own bucket or excluded via the toggle above (refunds inherit their expense's flag); account-flow-over-time aggregates `v_account_flow` by `(account_id, date)`; period comparison runs the same query over two ranges. The first reusable P2 query files live in `shared/queries/analysis_*.sql` and cover actual-by-category, account-flow-over-time, income-vs-expense, and period totals. All analysis in spec §4.8 reduces to filters/aggregations over these five views.

### 7.6 Per-trip actual total (list/detail rollup)

```sql
CREATE VIEW v_trip_actual_total AS
SELECT trip_id, SUM(amount_cents) AS total_actual_cents
FROM v_actual_expense
WHERE trip_id IS NOT NULL
GROUP BY trip_id;
```

`v_trip_actual_total` is a thin rollup over `v_actual_expense` used by the trip list/detail cards (`total_actual_cents`); it lives under `shared/queries/` like the other derived views so both apps join against one definition instead of pasting the same `GROUP BY trip_id` subquery per call site.

### 7.7 Movement summary (list/detail display rollup)

`v_movement_summary` is the single query every movement list/detail/filter surface joins against (P5R-3 `O1`, replacing four call sites that each re-derived the same display columns). It resolves account/category/trip/tag names, the linked-expense name and archived flag for a refund (§ orphan-refund banner), the "someone else paid" payer name for a shared expense, `is_shared`/`user_share_cents` for display, and — via a second `UNION ALL` branch — synthesizes an `'external_expense'` row for §2.6 external splits, which have no `movements` row of their own.

`amount_cents` means "the total cost of the expense" for **every** row type, including `external_expense` (sourced from `splits.total_amount_cents`, not the user's owed share); `user_share_cents` means "the user's own portion" for every row type (`-1` where not applicable). Before schema v5 the `external_expense` branch incorrectly sourced `amount_cents` from `split_lines.owed_amount_cents` (the user's own share), making the same column mean two different things depending on row type; fixed in `shared/migrations/005_fix_v_movement_summary_external_amount.sql`. This was numerically silent today only because the write path (`SplitRepository.createExternalPaidByPerson`/`replaceExternalSplit`) currently enforces a single debtor whose share equals the total (§2.6 v1 simplification, `docs/16-android-audit-findings.md` finding `O5`):

```sql
CREATE VIEW v_movement_summary AS
SELECT m.id, m.type, m.amount_cents, m.date,
       -- account/category/trip/tag names via LEFT JOIN
       -- refunds_expense_name/refunds_expense_archived: self-join on refunds_expense_id
       -- paid_by_person_name: the other participant when a shared expense's user line is 0
       -- is_shared (v_movement_shared) and user_share_cents (the user's own split line)
       ...
FROM movements m
JOIN accounts ON accounts.id = m.account_id
LEFT JOIN categories ON categories.id = m.category_id
LEFT JOIN trips ON trips.id = m.trip_id
LEFT JOIN tags ON tags.id = m.tag_id
LEFT JOIN accounts AS destination_accounts ON destination_accounts.id = m.dest_account_id
LEFT JOIN people AS settlement_people ON settlement_people.id = m.person_id
LEFT JOIN v_movement_shared ON v_movement_shared.movement_id = m.id
LEFT JOIN movements AS refunded_expense ON refunded_expense.id = m.refunds_expense_id
WHERE m.archived_at IS NULL
UNION ALL
-- §2.6 external split rows (no movements row): synthesizes type = 'external_expense'
SELECT s.id, 'external_expense', s.total_amount_cents, s.date, ...
FROM splits s
JOIN split_lines sl ON sl.split_id = s.id AND sl.participant_kind = 'user' AND sl.archived_at IS NULL
JOIN people p ON p.id = s.payer_person_id
WHERE s.movement_id IS NULL AND s.payer_person_id IS NOT NULL AND s.archived_at IS NULL;
```

Full column list and subquery detail live in `shared/queries/v_movement_summary.sql`. Unlike the other six views above — each touched exactly once, at `P0A-3` — `v_movement_summary` has been edited multiple times since (adding `tag_id`, then the refund-linked-expense columns, then the schema-v5 `external_expense.amount_cents` fix): any future edit must also update its embedded copy in `shared/migrations/002_add_splits_tag_id.sql` (the v1→v2 upgrader path) — an existing v1 install that only upgrades that far would otherwise drift out of sync with a fresh install's schema, the exact bug class documented in `docs/06-roadmap.md` P5R-6's and P5R-7's post-close fixes — though a later migration that also `DROP`/`CREATE`s the view (like `005_fix_v_movement_summary_external_amount.sql`) recreates the current definition for anyone who upgrades all the way, so only a partial-upgrade install stuck at v2 would ever observe the 002 copy.

---

Implementation note for the shared P2 queries: actual analysis uses `:one_time_mode` (`include`, `exclude`, `only`) and nullable `:category_nature` (`fixed`, `variable`) parameters. `only` applies to extraordinary expense rows and suppresses income rows for that view. Flow analysis remains based on `v_account_flow`; the flow-over-time query returns account bucket rows plus a shared bucket total for charting.

---

## 8. What remains

- **Full analysis-query set** for every §4.8 view (forecasting, top-merchants, heatmap, trends, savings-rate) — all follow the §7.5 patterns over the five views.
- **Migration scripts** (SQLDelight `.sq` migrations on Android; the matching ordered DDL on C#), kept byte-for-byte schema-identical.
- **Golden test vectors** (arch §4.2) for the procedural remainder: split rounding, recurring date-advancement, auto-cat matching + newer-wins tie-break, dedup, and the exact JSON payload schemas for `templates.split_config` and `auto_cat_rules.conditions`. **Drafted** in `docs/05-golden-tests.md`, with loadable fixtures under `shared/golden/`.

The schema and core derivations above are complete and internally consistent; these three are the remaining build artifacts.
