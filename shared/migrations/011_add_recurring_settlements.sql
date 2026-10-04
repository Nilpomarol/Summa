-- Gestor finances v10 -> v11 migration.
-- Recurring templates can now materialize settlements, and every settlement carries the
-- consumption scope ('all' or 'recurring') that the debt-consumption projection reads.
--
-- `templates` is rebuilt because widening its `type` CHECK is not expressible as ALTER TABLE.
-- `movements.template_id` is the only foreign key into `templates`, and DROP TABLE performs an
-- implicit DELETE that would orphan those rows. `defer_foreign_keys` does NOT rescue this: the
-- deferred-violation counter raised by that implicit DELETE is never cleared by the later rename,
-- so the COMMIT still fails. The links are therefore stashed, cleared, and restored explicitly.
-- No view references `templates`, so the rename cannot fail schema reparse.

CREATE TABLE templates_migration_backup (
    movement_id TEXT PRIMARY KEY,
    template_id TEXT NOT NULL
);

INSERT INTO templates_migration_backup(movement_id, template_id)
SELECT id, template_id FROM movements WHERE template_id IS NOT NULL;

UPDATE movements SET template_id = NULL WHERE template_id IS NOT NULL;

CREATE TABLE templates_new (
    id                     TEXT    PRIMARY KEY,
    type                   TEXT    NOT NULL CHECK (type IN ('expense','income','transfer','settlement')),
    amount_cents           INTEGER CHECK (amount_cents IS NULL OR amount_cents > 0),
    account_id             TEXT    NOT NULL REFERENCES accounts(id),
    dest_account_id        TEXT    REFERENCES accounts(id),
    category_id            TEXT    REFERENCES categories(id),
    tag_id                 TEXT    REFERENCES tags(id),
    trip_id                TEXT    REFERENCES trips(id),
    person_id              TEXT    REFERENCES people(id),
    settlement_direction   TEXT    CHECK (settlement_direction IN ('person_to_user','user_to_person')),
    settlement_scope       TEXT    CHECK (settlement_scope IN ('all','recurring')),
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
    CHECK ( (type = 'settlement') = (person_id IS NOT NULL) ),
    CHECK ( (type = 'settlement') = (settlement_direction IS NOT NULL) ),
    CHECK ( (type = 'settlement') = (settlement_scope IS NOT NULL) ),
    CHECK ( type <> 'settlement' OR (trip_id IS NULL AND split_config IS NULL) ),
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

INSERT INTO templates_new(
    id, type, amount_cents, account_id, dest_account_id, category_id, tag_id, trip_id,
    name, payee, notes, split_config, frequency, interval_count, custom_unit, day_of_month,
    weekday, next_due_date, amount_is_variable, amount_flex_cents, date_flex_days,
    lead_notification_days, status, created_at, updated_at, archived_at
)
SELECT
    id, type, amount_cents, account_id, dest_account_id, category_id, tag_id, trip_id,
    name, payee, notes, split_config, frequency, interval_count, custom_unit, day_of_month,
    weekday, next_due_date, amount_is_variable, amount_flex_cents, date_flex_days,
    lead_notification_days, status, created_at, updated_at, archived_at
FROM templates;

DROP TABLE templates;
ALTER TABLE templates_new RENAME TO templates;

CREATE INDEX idx_templates_next_due
    ON templates(next_due_date)
    WHERE status = 'active';

UPDATE movements
SET template_id = (
        SELECT b.template_id
        FROM templates_migration_backup b
        WHERE b.movement_id = movements.id
    )
WHERE id IN (SELECT movement_id FROM templates_migration_backup);

DROP TABLE templates_migration_backup;

-- Existing settlements consumed debt without any scope restriction, so 'all' preserves their
-- meaning exactly. The CHECK tying scope to type lives in the fresh-install schema only, matching
-- how migrations 002 and 003 added columns without retrofitting multi-column CHECKs for upgraders.
-- Semicolons are deliberately kept out of comments so naive statement splitters cannot mis-cut.
ALTER TABLE movements ADD COLUMN settlement_scope TEXT;

UPDATE movements SET settlement_scope = 'all' WHERE type = 'settlement';

UPDATE meta SET value = '11' WHERE key = 'schema_version';
