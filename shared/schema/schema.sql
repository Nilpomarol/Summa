-- Gestor finances shared SQLite schema.
-- Source of truth: docs/data-contract.md.

PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;

CREATE TABLE accounts (
    id                          TEXT    PRIMARY KEY,
    name                        TEXT    NOT NULL,
    starting_balance_cents      INTEGER NOT NULL DEFAULT 0,
    type                        TEXT    NOT NULL CHECK (type IN ('bank','cash','savings','investment','other')),
    icon                        TEXT,
    color                       TEXT,
    is_default                  INTEGER NOT NULL DEFAULT 0 CHECK (is_default IN (0,1)),
    display_order               INTEGER NOT NULL DEFAULT 0,
    low_balance_threshold_cents INTEGER,
    created_at                  TEXT    NOT NULL,
    updated_at                  TEXT    NOT NULL,
    archived_at                 TEXT
);

CREATE UNIQUE INDEX idx_accounts_one_default
    ON accounts(is_default)
    WHERE is_default = 1;

CREATE TABLE categories (
    id            TEXT    PRIMARY KEY,
    name          TEXT    NOT NULL,
    kind          TEXT    NOT NULL CHECK (kind IN ('expense','income','both')),
    nature        TEXT    NOT NULL CHECK (nature IN ('fixed','variable')),
    parent_id     TEXT    REFERENCES categories(id),
    icon          TEXT,
    color         TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at    TEXT    NOT NULL,
    updated_at    TEXT    NOT NULL,
    archived_at   TEXT
);

CREATE INDEX idx_categories_parent
    ON categories(parent_id)
    WHERE parent_id IS NOT NULL;

CREATE TABLE people (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    avatar      TEXT,
    color       TEXT,
    notes       TEXT,
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL,
    archived_at TEXT
);

CREATE TABLE trips (
    id                 TEXT PRIMARY KEY,
    name               TEXT NOT NULL,
    type               TEXT NOT NULL CHECK (type IN ('trip','celebration','other')),
    status             TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('planned','active','finished')),
    start_date         TEXT,
    end_date           TEXT,
    icon               TEXT,
    color              TEXT,
    notes              TEXT,
    default_account_id TEXT REFERENCES accounts(id),
    created_at         TEXT NOT NULL,
    updated_at         TEXT NOT NULL,
    archived_at        TEXT
);

CREATE TABLE tags (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    icon        TEXT,
    color       TEXT,
    trip_id     TEXT REFERENCES trips(id),
    category_id TEXT REFERENCES categories(id),
    trip_type   TEXT,
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL,
    archived_at TEXT,

    CHECK ( trip_id IS NULL OR trip_type IS NULL )
);

CREATE INDEX idx_tags_trip
    ON tags(trip_id)
    WHERE trip_id IS NOT NULL;

CREATE TABLE templates (
    id                     TEXT    PRIMARY KEY,
    type                   TEXT    NOT NULL CHECK (type IN ('expense','income','transfer')),
    amount_cents           INTEGER CHECK (amount_cents IS NULL OR amount_cents > 0),
    account_id             TEXT    NOT NULL REFERENCES accounts(id),
    dest_account_id        TEXT    REFERENCES accounts(id),
    category_id            TEXT    REFERENCES categories(id),
    tag_id                 TEXT    REFERENCES tags(id),
    trip_id                TEXT    REFERENCES trips(id),
    name                   TEXT,
    payee                  TEXT,
    notes                  TEXT,
    split_config           TEXT,
    frequency              TEXT    NOT NULL CHECK (frequency IN ('weekly','fortnightly','monthly','yearly','custom')),
    interval_count         INTEGER,
    custom_unit            TEXT    CHECK (custom_unit IN ('days','weeks','months','years')),
    day_of_month           INTEGER CHECK (day_of_month BETWEEN 1 AND 31),
    weekday                INTEGER CHECK (weekday BETWEEN 0 AND 6),
    next_due_date          TEXT    NOT NULL,
    amount_is_variable     INTEGER NOT NULL DEFAULT 0 CHECK (amount_is_variable IN (0,1)),
    amount_flex_cents      INTEGER,
    date_flex_days         INTEGER,
    lead_notification_days INTEGER,
    status                 TEXT    NOT NULL DEFAULT 'active' CHECK (status IN ('active','paused','ended')),
    created_at             TEXT    NOT NULL,
    updated_at             TEXT    NOT NULL,
    archived_at            TEXT,

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

CREATE INDEX idx_templates_next_due
    ON templates(next_due_date)
    WHERE status = 'active';

CREATE TABLE budgets (
    id                      TEXT    PRIMARY KEY,
    scope                   TEXT    NOT NULL CHECK (scope IN ('category','overall_month','trip')),
    category_id             TEXT    REFERENCES categories(id),
    trip_id                 TEXT    REFERENCES trips(id),
    period                  TEXT    NOT NULL CHECK (period IN ('monthly','yearly','one_off')),
    limit_amount_cents      INTEGER NOT NULL CHECK (limit_amount_cents > 0),
    alert_threshold_percent INTEGER CHECK (alert_threshold_percent BETWEEN 1 AND 100),
    include_trip_expenses          INTEGER NOT NULL DEFAULT 1 CHECK (include_trip_expenses IN (0,1)),
    include_extraordinary_expenses INTEGER NOT NULL DEFAULT 1 CHECK (include_extraordinary_expenses IN (0,1)),
    created_at              TEXT    NOT NULL,
    updated_at              TEXT    NOT NULL,
    archived_at             TEXT,

    CHECK (
        (scope='category'      AND category_id IS NOT NULL AND trip_id IS NULL) OR
        (scope='trip'          AND trip_id     IS NOT NULL AND category_id IS NULL) OR
        (scope='overall_month' AND category_id IS NULL     AND trip_id IS NULL)
    ),
    CHECK (
        (scope='category' AND period IN ('monthly','yearly')) OR
        (scope='trip' AND period = 'one_off') OR
        (scope='overall_month' AND period = 'monthly')
    )
);

CREATE TABLE auto_cat_rules (
    id                 TEXT    PRIMARY KEY,
    name               TEXT    NOT NULL,
    priority           INTEGER NOT NULL,
    conditions         TEXT    NOT NULL,
    action_category_id TEXT    REFERENCES categories(id),
    action_trip_id     TEXT    REFERENCES trips(id),
    source             TEXT    NOT NULL CHECK (source IN ('user','system_learned')),
    active             INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0,1)),
    created_at         TEXT    NOT NULL,
    updated_at         TEXT    NOT NULL,
    archived_at        TEXT
);

CREATE INDEX idx_rules_match
    ON auto_cat_rules(priority DESC, created_at DESC)
    WHERE active = 1;

CREATE TABLE import_batches (
    id          TEXT    PRIMARY KEY,
    source_file TEXT,
    account_id  TEXT    NOT NULL REFERENCES accounts(id),
    row_count   INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL,
    archived_at TEXT
);

CREATE TABLE meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE movements (
    id                   TEXT    PRIMARY KEY,
    type                 TEXT    NOT NULL CHECK (type IN ('expense','income','transfer','settlement','refund')),
    amount_cents         INTEGER NOT NULL CHECK (amount_cents > 0),
    date                 TEXT    NOT NULL,
    account_id           TEXT    NOT NULL REFERENCES accounts(id),
    dest_account_id      TEXT    REFERENCES accounts(id),
    name                 TEXT,
    payee                TEXT,
    notes                TEXT,
    is_one_time          INTEGER NOT NULL DEFAULT 0 CHECK (is_one_time IN (0,1)),
    category_id          TEXT    REFERENCES categories(id),
    tag_id               TEXT    REFERENCES tags(id),
    trip_id              TEXT    REFERENCES trips(id),
    template_id          TEXT    REFERENCES templates(id),
    import_batch_id      TEXT    REFERENCES import_batches(id),
    person_id            TEXT    REFERENCES people(id),
    settlement_direction TEXT    CHECK (settlement_direction IN ('person_to_user','user_to_person')),
    refunds_expense_id   TEXT    REFERENCES movements(id),
    actual_refund_cents  INTEGER CHECK (actual_refund_cents IS NULL OR actual_refund_cents >= 0),
    created_at           TEXT    NOT NULL,
    updated_at           TEXT    NOT NULL,
    archived_at          TEXT,

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

CREATE INDEX idx_movements_date
    ON movements(date);

CREATE INDEX idx_movements_account_date
    ON movements(account_id, date);

CREATE INDEX idx_movements_dest_account
    ON movements(dest_account_id)
    WHERE dest_account_id IS NOT NULL;

CREATE INDEX idx_movements_category_date
    ON movements(category_id, date)
    WHERE category_id IS NOT NULL;

CREATE INDEX idx_movements_trip
    ON movements(trip_id)
    WHERE trip_id IS NOT NULL;

CREATE INDEX idx_movements_person
    ON movements(person_id)
    WHERE person_id IS NOT NULL;

CREATE INDEX idx_movements_template
    ON movements(template_id)
    WHERE template_id IS NOT NULL;

CREATE INDEX idx_movements_refunds
    ON movements(refunds_expense_id)
    WHERE refunds_expense_id IS NOT NULL;

CREATE INDEX idx_movements_import_batch
    ON movements(import_batch_id)
    WHERE import_batch_id IS NOT NULL;

CREATE TABLE splits (
    id                 TEXT    PRIMARY KEY,
    movement_id        TEXT    REFERENCES movements(id) ON DELETE CASCADE,
    payer_person_id    TEXT    REFERENCES people(id),
    entry_method       TEXT    NOT NULL CHECK (entry_method IN ('equal','exact','percentage')),
    total_amount_cents INTEGER CHECK (total_amount_cents IS NULL OR total_amount_cents > 0),
    date               TEXT,
    description        TEXT,
    category_id        TEXT    REFERENCES categories(id),
    trip_id            TEXT    REFERENCES trips(id),
    tag_id             TEXT    REFERENCES tags(id),
    created_at         TEXT    NOT NULL,
    updated_at         TEXT    NOT NULL,
    archived_at        TEXT,

    CHECK (
        (payer_person_id IS NULL AND movement_id IS NOT NULL
         AND total_amount_cents IS NULL AND date IS NULL
         AND description IS NULL AND category_id IS NULL AND trip_id IS NULL
         AND tag_id IS NULL)
        OR
        (payer_person_id IS NOT NULL AND movement_id IS NULL
         AND total_amount_cents IS NOT NULL AND date IS NOT NULL)
    ),
    CHECK ( tag_id IS NULL OR trip_id IS NOT NULL )
);

CREATE UNIQUE INDEX idx_splits_movement
    ON splits(movement_id)
    WHERE movement_id IS NOT NULL;

CREATE INDEX idx_splits_payer
    ON splits(payer_person_id)
    WHERE payer_person_id IS NOT NULL;

CREATE TABLE split_lines (
    id                TEXT    PRIMARY KEY,
    split_id          TEXT    NOT NULL REFERENCES splits(id) ON DELETE CASCADE,
    participant_kind  TEXT    NOT NULL CHECK (participant_kind IN ('user','person')),
    person_id         TEXT    REFERENCES people(id),
    owed_amount_cents INTEGER NOT NULL CHECK (owed_amount_cents >= 0),
    owed_percent      REAL,
    created_at        TEXT    NOT NULL,
    updated_at        TEXT    NOT NULL,
    archived_at       TEXT,

    CHECK ( (participant_kind = 'user') = (person_id IS NULL) )
);

CREATE INDEX idx_split_lines_split
    ON split_lines(split_id);

CREATE INDEX idx_split_lines_person
    ON split_lines(person_id)
    WHERE person_id IS NOT NULL;

CREATE UNIQUE INDEX idx_split_lines_one_user
    ON split_lines(split_id)
    WHERE participant_kind = 'user' AND archived_at IS NULL;

CREATE UNIQUE INDEX idx_split_lines_one_person
    ON split_lines(split_id, person_id)
    WHERE participant_kind = 'person' AND archived_at IS NULL;
