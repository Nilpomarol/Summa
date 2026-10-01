-- Frozen v19 fresh-install schema (schema.sql, migration 016's triggers, and the canonical views
-- as of schema version 19). MigrationTest builds the database each older migration step was
-- written against from it. Statements are separated by `-- @statement` lines.
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
    ownership_kind              TEXT    NOT NULL DEFAULT 'personal' CHECK (ownership_kind IN ('personal','shared')),
    created_at                  TEXT    NOT NULL,
    updated_at                  TEXT    NOT NULL,
    archived_at                 TEXT
);
-- @statement
CREATE UNIQUE INDEX idx_accounts_one_default
    ON accounts(is_default)
    WHERE is_default = 1;
-- @statement
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
-- @statement
CREATE INDEX idx_categories_parent
    ON categories(parent_id)
    WHERE parent_id IS NOT NULL;
-- @statement
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
-- @statement
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
-- @statement
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
-- @statement
CREATE INDEX idx_tags_trip
    ON tags(trip_id)
    WHERE trip_id IS NOT NULL;
-- @statement
CREATE TABLE templates (
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
-- @statement
CREATE INDEX idx_templates_next_due
    ON templates(next_due_date)
    WHERE status = 'active';
-- @statement
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
-- @statement
CREATE TABLE import_batches (
    id          TEXT    PRIMARY KEY,
    source_file TEXT,
    account_id  TEXT    NOT NULL REFERENCES accounts(id),
    row_count   INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL,
    archived_at TEXT
);
-- @statement
CREATE TABLE meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
-- @statement
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
    settlement_scope     TEXT    CHECK (settlement_scope IN ('all','recurring')),
    refunds_expense_id   TEXT    REFERENCES movements(id),
    actual_refund_cents  INTEGER CHECK (actual_refund_cents IS NULL OR actual_refund_cents >= 0),
    expense_funding      TEXT    CHECK (expense_funding IN ('owner','shared_account')),
    shared_split_id      TEXT    REFERENCES splits(id) DEFERRABLE INITIALLY DEFERRED,
    created_at           TEXT    NOT NULL,
    updated_at           TEXT    NOT NULL,
    archived_at          TEXT,

    CHECK ( (type = 'transfer')   = (dest_account_id IS NOT NULL) ),
    CHECK ( dest_account_id IS NULL OR dest_account_id <> account_id ),
    CHECK ( category_id IS NULL OR type IN ('expense','income','refund') ),
    CHECK ( tag_id IS NULL OR trip_id IS NOT NULL ),
    CHECK ( (type = 'settlement') = (person_id IS NOT NULL) ),
    CHECK ( (type = 'settlement') = (settlement_direction IS NOT NULL) ),
    CHECK ( (type = 'settlement') = (settlement_scope IS NOT NULL) ),
    CHECK ( (type = 'refund')     = (refunds_expense_id IS NOT NULL) ),
    CHECK ( actual_refund_cents IS NULL OR type = 'refund' ),
    CHECK ( actual_refund_cents IS NULL OR actual_refund_cents <= amount_cents ),
    CHECK ( is_one_time = 0 OR type = 'expense' )
);
-- @statement
CREATE INDEX idx_movements_date
    ON movements(date);
-- @statement
CREATE INDEX idx_movements_account_date
    ON movements(account_id, date);
-- @statement
CREATE INDEX idx_movements_dest_account
    ON movements(dest_account_id)
    WHERE dest_account_id IS NOT NULL;
-- @statement
CREATE INDEX idx_movements_category_date
    ON movements(category_id, date)
    WHERE category_id IS NOT NULL;
-- @statement
CREATE INDEX idx_movements_trip
    ON movements(trip_id)
    WHERE trip_id IS NOT NULL;
-- @statement
CREATE INDEX idx_movements_person
    ON movements(person_id)
    WHERE person_id IS NOT NULL;
-- @statement
CREATE INDEX idx_movements_template
    ON movements(template_id)
    WHERE template_id IS NOT NULL;
-- @statement
CREATE INDEX idx_movements_refunds
    ON movements(refunds_expense_id)
    WHERE refunds_expense_id IS NOT NULL;
-- @statement
CREATE INDEX idx_movements_import_batch
    ON movements(import_batch_id)
    WHERE import_batch_id IS NOT NULL;
-- @statement
CREATE TABLE account_contributions (
    id                 TEXT    PRIMARY KEY,
    shared_account_id  TEXT    NOT NULL REFERENCES accounts(id),
    direction          TEXT    NOT NULL DEFAULT 'in' CHECK (direction IN ('in','out')),
    contributor_kind   TEXT    NOT NULL CHECK (contributor_kind IN ('user','person')),
    person_id          TEXT    REFERENCES people(id),
    source_account_id  TEXT    REFERENCES accounts(id),
    amount_cents       INTEGER NOT NULL CHECK (amount_cents > 0),
    date               TEXT    NOT NULL,
    name               TEXT,
    notes              TEXT,
    created_at         TEXT    NOT NULL,
    updated_at         TEXT    NOT NULL,
    archived_at        TEXT,

    CHECK ( (contributor_kind = 'user') = (person_id IS NULL) ),
    CHECK ( contributor_kind = 'user' OR source_account_id IS NULL ),
    CHECK ( source_account_id IS NULL OR source_account_id <> shared_account_id )
);
-- @statement
CREATE INDEX idx_account_contributions_shared_date
    ON account_contributions(shared_account_id, date);
-- @statement
CREATE INDEX idx_account_contributions_source_date
    ON account_contributions(source_account_id, date)
    WHERE source_account_id IS NOT NULL;
-- @statement
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
-- @statement
CREATE UNIQUE INDEX idx_splits_movement
    ON splits(movement_id)
    WHERE movement_id IS NOT NULL;
-- @statement
CREATE INDEX idx_splits_payer
    ON splits(payer_person_id)
    WHERE payer_person_id IS NOT NULL;
-- @statement
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
-- @statement
CREATE INDEX idx_split_lines_split
    ON split_lines(split_id);
-- @statement
CREATE INDEX idx_split_lines_person
    ON split_lines(person_id)
    WHERE person_id IS NOT NULL;
-- @statement
CREATE UNIQUE INDEX idx_split_lines_one_user
    ON split_lines(split_id)
    WHERE participant_kind = 'user' AND archived_at IS NULL;
-- @statement
CREATE UNIQUE INDEX idx_split_lines_one_person
    ON split_lines(split_id, person_id)
    WHERE participant_kind = 'person' AND archived_at IS NULL;
-- @statement
CREATE TABLE goals (
    id                  TEXT    PRIMARY KEY,
    name                TEXT    NOT NULL,
    target_amount_cents INTEGER NOT NULL CHECK (target_amount_cents > 0),
    target_date         TEXT,
    account_id          TEXT    REFERENCES accounts(id),
    funding_mode        TEXT    NOT NULL CHECK (funding_mode IN ('dedicated_account','allocations')),
    status              TEXT    NOT NULL DEFAULT 'active' CHECK (status IN ('active','paused','completed')),
    icon                TEXT,
    color               TEXT,
    display_order       INTEGER NOT NULL DEFAULT 0,
    notes               TEXT,
    created_at          TEXT    NOT NULL,
    updated_at          TEXT    NOT NULL,
    archived_at         TEXT,

    CHECK ( funding_mode <> 'dedicated_account' OR account_id IS NOT NULL )
);
-- @statement
CREATE TABLE account_members (
    id                           TEXT    PRIMARY KEY,
    account_id                   TEXT    NOT NULL REFERENCES accounts(id),
    participant_kind             TEXT    NOT NULL CHECK (participant_kind IN ('user','person')),
    person_id                    TEXT    REFERENCES people(id),
    ownership_basis_points       INTEGER NOT NULL CHECK (ownership_basis_points BETWEEN 0 AND 10000),
    default_expense_basis_points INTEGER NOT NULL CHECK (default_expense_basis_points BETWEEN 0 AND 10000),
    created_at                   TEXT    NOT NULL,
    updated_at                   TEXT    NOT NULL,
    archived_at                  TEXT,

    CHECK ( (participant_kind = 'user') = (person_id IS NULL) )
);
-- @statement
CREATE INDEX idx_account_members_account
    ON account_members(account_id);
-- @statement
CREATE UNIQUE INDEX idx_account_members_one_user
    ON account_members(account_id)
    WHERE participant_kind = 'user' AND archived_at IS NULL;
-- @statement
CREATE UNIQUE INDEX idx_account_members_one_person
    ON account_members(account_id, person_id)
    WHERE participant_kind = 'person' AND archived_at IS NULL;
-- @statement
CREATE INDEX idx_goals_account
    ON goals(account_id)
    WHERE account_id IS NOT NULL;
-- @statement
CREATE TABLE goal_allocations (
    id           TEXT    PRIMARY KEY,
    goal_id      TEXT    NOT NULL REFERENCES goals(id),
    account_id   TEXT    NOT NULL REFERENCES accounts(id),
    date         TEXT    NOT NULL,
    amount_cents INTEGER NOT NULL CHECK (amount_cents <> 0),
    notes        TEXT,
    created_at   TEXT    NOT NULL,
    updated_at   TEXT    NOT NULL,
    archived_at  TEXT
);
-- @statement
CREATE INDEX idx_goal_allocations_goal
    ON goal_allocations(goal_id);
-- @statement
CREATE INDEX idx_goal_allocations_account_date
    ON goal_allocations(account_id, date);
-- @statement
INSERT INTO meta (key, value) VALUES ('schema_version', '19'), ('snapshot_version', '0');
-- @statement
CREATE TRIGGER account_reject_direct_shared_insert
BEFORE INSERT ON accounts FOR EACH ROW
WHEN NEW.ownership_kind = 'shared'
BEGIN
    SELECT RAISE(ABORT, 'Create the account as personal, add members, then mark it shared.');
END;
-- @statement
CREATE TRIGGER account_validate_members_before_becoming_shared
BEFORE UPDATE OF ownership_kind, archived_at ON accounts FOR EACH ROW
WHEN NEW.ownership_kind = 'shared'
 AND (OLD.ownership_kind <> 'shared' OR (OLD.archived_at IS NOT NULL AND NEW.archived_at IS NULL))
BEGIN
    SELECT CASE WHEN (
        SELECT COUNT(*) FROM account_members
        WHERE account_id = NEW.id AND participant_kind = 'user' AND archived_at IS NULL
    ) <> 1 THEN RAISE(ABORT, 'A shared account requires exactly one owner member.') END;
    SELECT CASE WHEN (
        SELECT COUNT(*) FROM account_members am
        JOIN people p ON p.id = am.person_id AND p.archived_at IS NULL
        WHERE am.account_id = NEW.id AND am.participant_kind = 'person' AND am.archived_at IS NULL
    ) = 0 THEN RAISE(ABORT, 'A shared account requires at least one active person member.') END;
    SELECT CASE WHEN (
        SELECT COALESCE(SUM(ownership_basis_points), 0) FROM account_members
        WHERE account_id = NEW.id AND archived_at IS NULL
    ) <> 10000 THEN RAISE(ABORT, 'Shared ownership percentages must total 100%.') END;
    SELECT CASE WHEN (
        SELECT COALESCE(SUM(default_expense_basis_points), 0) FROM account_members
        WHERE account_id = NEW.id AND archived_at IS NULL
    ) <> 10000 THEN RAISE(ABORT, 'Shared expense percentages must total 100%.') END;
END;
-- @statement
CREATE TRIGGER account_members_reject_insert_for_shared_account
BEFORE INSERT ON account_members FOR EACH ROW
WHEN EXISTS (SELECT 1 FROM accounts WHERE id = NEW.account_id AND ownership_kind = 'shared')
BEGIN
    SELECT RAISE(ABORT, 'Stage membership changes while the account is personal.');
END;
-- @statement
CREATE TRIGGER account_members_reject_update_for_shared_account
BEFORE UPDATE ON account_members FOR EACH ROW
WHEN EXISTS (SELECT 1 FROM accounts WHERE id = OLD.account_id AND ownership_kind = 'shared')
 OR EXISTS (SELECT 1 FROM accounts WHERE id = NEW.account_id AND ownership_kind = 'shared')
BEGIN
    SELECT RAISE(ABORT, 'Stage membership changes while the account is personal.');
END;
-- @statement
CREATE TRIGGER account_members_reject_delete_for_shared_account
BEFORE DELETE ON account_members FOR EACH ROW
WHEN EXISTS (SELECT 1 FROM accounts WHERE id = OLD.account_id AND ownership_kind = 'shared')
BEGIN
    SELECT RAISE(ABORT, 'Stage membership changes while the account is personal.');
END;
-- @statement
CREATE TRIGGER account_contributions_validate_insert
BEFORE INSERT ON account_contributions FOR EACH ROW
BEGIN
    SELECT CASE WHEN NOT EXISTS (
        SELECT 1 FROM accounts
        WHERE id = NEW.shared_account_id AND ownership_kind = 'shared' AND archived_at IS NULL
    ) THEN RAISE(ABORT, 'A contribution must target an active shared account.') END;
    SELECT CASE WHEN NOT EXISTS (
        SELECT 1 FROM account_members am
        LEFT JOIN people p ON p.id = am.person_id
        WHERE am.account_id = NEW.shared_account_id
          AND am.participant_kind = NEW.contributor_kind
          AND (am.person_id IS NEW.person_id)
          AND am.archived_at IS NULL
          AND (am.participant_kind = 'user' OR p.archived_at IS NULL)
    ) THEN RAISE(ABORT, 'A contribution must come from an active account member.') END;
    SELECT CASE WHEN NEW.source_account_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM accounts
        WHERE id = NEW.source_account_id AND ownership_kind = 'personal' AND archived_at IS NULL
    ) THEN RAISE(ABORT, 'A contribution source must be an active personal account.') END;
END;
-- @statement
CREATE TRIGGER account_contributions_validate_identity_update
BEFORE UPDATE OF shared_account_id, contributor_kind, person_id, source_account_id ON account_contributions FOR EACH ROW
WHEN NEW.archived_at IS NULL
BEGIN
    SELECT CASE WHEN NOT EXISTS (
        SELECT 1 FROM accounts
        WHERE id = NEW.shared_account_id AND ownership_kind = 'shared' AND archived_at IS NULL
    ) THEN RAISE(ABORT, 'A contribution must target an active shared account.') END;
    SELECT CASE WHEN NOT EXISTS (
        SELECT 1 FROM account_members am
        LEFT JOIN people p ON p.id = am.person_id
        WHERE am.account_id = NEW.shared_account_id
          AND am.participant_kind = NEW.contributor_kind
          AND (am.person_id IS NEW.person_id)
          AND am.archived_at IS NULL
          AND (am.participant_kind = 'user' OR p.archived_at IS NULL)
    ) THEN RAISE(ABORT, 'A contribution must come from an active account member.') END;
    SELECT CASE WHEN NEW.source_account_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM accounts
        WHERE id = NEW.source_account_id AND ownership_kind = 'personal' AND archived_at IS NULL
    ) THEN RAISE(ABORT, 'A contribution source must be an active personal account.') END;
END;
-- @statement
CREATE TRIGGER movements_validate_shared_expense_funding_insert
BEFORE INSERT ON movements FOR EACH ROW
WHEN NEW.expense_funding IS NOT NULL
BEGIN
    SELECT CASE WHEN NEW.type <> 'expense' THEN RAISE(ABORT, 'Only an expense may declare financing.') END;
    SELECT CASE WHEN NEW.expense_funding = 'shared_account' AND NOT EXISTS (
        SELECT 1 FROM accounts WHERE id = NEW.account_id AND ownership_kind = 'shared'
    ) THEN RAISE(ABORT, 'Shared-account financing requires a shared account.') END;
    SELECT CASE WHEN NEW.expense_funding = 'owner' AND EXISTS (
        SELECT 1 FROM accounts WHERE id = NEW.account_id AND ownership_kind = 'shared'
    ) THEN RAISE(ABORT, 'Expenses from a shared account require shared-account financing.') END;
    SELECT CASE WHEN NEW.expense_funding = 'shared_account' AND NEW.shared_split_id IS NULL
        THEN RAISE(ABORT, 'A shared-account expense must name the split it is consumed through.') END;
END;
-- @statement
CREATE TRIGGER movements_validate_shared_expense_funding_update
BEFORE UPDATE OF type, account_id, expense_funding, shared_split_id ON movements FOR EACH ROW
WHEN NEW.expense_funding IS NOT NULL
BEGIN
    SELECT CASE WHEN NEW.type <> 'expense' THEN RAISE(ABORT, 'Only an expense may declare financing.') END;
    SELECT CASE WHEN NEW.expense_funding = 'shared_account' AND NOT EXISTS (
        SELECT 1 FROM accounts WHERE id = NEW.account_id AND ownership_kind = 'shared'
    ) THEN RAISE(ABORT, 'Shared-account financing requires a shared account.') END;
    SELECT CASE WHEN NEW.expense_funding = 'owner' AND EXISTS (
        SELECT 1 FROM accounts WHERE id = NEW.account_id AND ownership_kind = 'shared'
    ) THEN RAISE(ABORT, 'Expenses from a shared account require shared-account financing.') END;
    SELECT CASE WHEN NEW.expense_funding = 'shared_account' AND NEW.shared_split_id IS NULL
        THEN RAISE(ABORT, 'A shared-account expense must name the split it is consumed through.') END;
END;
-- @statement
CREATE TRIGGER splits_reject_archive_for_active_shared_expense
BEFORE UPDATE OF archived_at ON splits FOR EACH ROW
WHEN NEW.archived_at IS NOT NULL
 AND EXISTS (
    SELECT 1 FROM movements
    WHERE id = OLD.movement_id
      AND archived_at IS NULL
      AND expense_funding = 'shared_account'
 )
BEGIN
    SELECT RAISE(ABORT, 'An active shared-account expense must retain its split.');
END;
-- @statement
CREATE VIEW v_movement_shared AS
SELECT
    m.id AS movement_id,
    EXISTS (
        SELECT 1
        FROM splits s
        WHERE s.movement_id = m.id
          AND s.archived_at IS NULL
    ) AS is_shared
FROM movements m;
-- @statement
CREATE VIEW v_movement_summary AS
SELECT
    movements.id,
    movements.type,
    movements.amount_cents,
    movements.date,
    movements.account_id,
    accounts.name AS account_name,
    accounts.color AS account_color,
    movements.dest_account_id,
    destination_accounts.name AS destination_account_name,
    destination_accounts.color AS destination_account_color,
    movements.category_id,
    categories.name AS category_name,
    categories.nature AS category_nature,
    categories.icon AS category_icon,
    categories.color AS category_color,
    movements.trip_id,
    trips.name AS trip_name,
    trips.color AS trip_color,
    movements.tag_id,
    tags.name AS tag_name,
    movements.template_id,
    movements.name,
    movements.payee,
    movements.notes,
    movements.is_one_time,
    movements.created_at,
    movements.updated_at,
    movements.archived_at,
    movements.refunds_expense_id,
    refunded_expense.name AS refunds_expense_name,
    CASE WHEN refunded_expense.archived_at IS NOT NULL THEN 1 ELSE 0 END AS refunds_expense_archived,
    NULLIF((
        SELECT paid_by_people.name
        FROM splits AS paid_by_split
        JOIN split_lines AS paid_by_user_line
            ON paid_by_user_line.split_id = paid_by_split.id
           AND paid_by_user_line.participant_kind = 'user'
           AND paid_by_user_line.owed_amount_cents = 0
           AND paid_by_user_line.archived_at IS NULL
        JOIN split_lines AS paid_by_person_line
            ON paid_by_person_line.split_id = paid_by_split.id
           AND paid_by_person_line.participant_kind = 'person'
           AND paid_by_person_line.owed_amount_cents = movements.amount_cents
           AND paid_by_person_line.archived_at IS NULL
        JOIN people AS paid_by_people
            ON paid_by_people.id = paid_by_person_line.person_id
        WHERE paid_by_split.movement_id = movements.id
          AND paid_by_split.archived_at IS NULL
          AND (
              SELECT COUNT(*)
              FROM split_lines AS positive_person_line
              WHERE positive_person_line.split_id = paid_by_split.id
                AND positive_person_line.participant_kind = 'person'
                AND positive_person_line.owed_amount_cents > 0
                AND positive_person_line.archived_at IS NULL
          ) = 1
        LIMIT 1
    ), '') AS paid_by_person_name,
    COALESCE(v_movement_shared.is_shared, 0) AS is_shared,
    CASE
        WHEN movements.type = 'expense' THEN COALESCE(movements.expense_funding, 'owner')
        WHEN movements.type IN ('income','transfer','settlement','refund') THEN 'owner'
        ELSE NULL
    END AS financing_kind,
    movements.person_id AS payer_id,
    movements.settlement_direction,
    settlement_people.name AS settlement_person_name,
    CASE
        WHEN v_movement_shared.is_shared = 1 AND movements.type IN ('expense', 'income')
        THEN COALESCE((
            SELECT sl.owed_amount_cents
            FROM splits s
            JOIN split_lines sl
                ON sl.split_id = s.id
               AND sl.participant_kind = 'user'
               AND sl.archived_at IS NULL
            WHERE s.movement_id = movements.id
              AND s.archived_at IS NULL
            LIMIT 1
        ), -1)
        ELSE -1
    END AS user_share_cents,
    CASE WHEN movements.template_id IS NOT NULL THEN 1 ELSE 0 END AS is_recurring,
    NULL AS contribution_direction
FROM movements
JOIN accounts
    ON accounts.id = movements.account_id
LEFT JOIN accounts AS destination_accounts
    ON destination_accounts.id = movements.dest_account_id
LEFT JOIN categories
    ON categories.id = movements.category_id
LEFT JOIN trips
    ON trips.id = movements.trip_id
LEFT JOIN tags
    ON tags.id = movements.tag_id
LEFT JOIN people AS settlement_people
    ON settlement_people.id = movements.person_id
LEFT JOIN v_movement_shared
    ON v_movement_shared.movement_id = movements.id
LEFT JOIN movements AS refunded_expense
    ON refunded_expense.id = movements.refunds_expense_id
WHERE movements.archived_at IS NULL

UNION ALL

SELECT
    s.id,
    'external_expense' AS type,
    s.total_amount_cents AS amount_cents,
    IFNULL(s.date, '') AS date,
    NULL AS account_id,
    NULL AS account_name,
    NULL AS account_color,
    NULL AS dest_account_id,
    NULL AS destination_account_name,
    NULL AS destination_account_color,
    s.category_id,
    c.name AS category_name,
    c.nature AS category_nature,
    c.icon AS category_icon,
    c.color AS category_color,
    s.trip_id,
    t.name AS trip_name,
    t.color AS trip_color,
    s.tag_id,
    tg.name AS tag_name,
    NULL AS template_id,
    s.description AS name,
    NULL AS payee,
    NULL AS notes,
    0 AS is_one_time,
    s.created_at,
    s.updated_at,
    s.archived_at,
    NULL AS refunds_expense_id,
    NULL AS refunds_expense_name,
    0 AS refunds_expense_archived,
    p.name AS paid_by_person_name,
    0 AS is_shared,
    'person' AS financing_kind,
    p.id AS payer_id,
    NULL AS settlement_direction,
    NULL AS settlement_person_name,
    sl.owed_amount_cents AS user_share_cents,
    0 AS is_recurring,
    NULL AS contribution_direction
FROM splits s
JOIN split_lines sl
    ON sl.split_id = s.id
   AND sl.participant_kind = 'user'
   AND sl.archived_at IS NULL
JOIN people p ON p.id = s.payer_person_id
LEFT JOIN categories c ON c.id = s.category_id
LEFT JOIN trips t ON t.id = s.trip_id
LEFT JOIN tags tg ON tg.id = s.tag_id
WHERE s.movement_id IS NULL
  AND s.payer_person_id IS NOT NULL
  AND s.archived_at IS NULL

UNION ALL

SELECT
    contribution.id,
    'contribution' AS type,
    contribution.amount_cents,
    contribution.date,
    contribution.source_account_id AS account_id,
    source_account.name AS account_name,
    source_account.color AS account_color,
    contribution.shared_account_id AS dest_account_id,
    shared_account.name AS destination_account_name,
    shared_account.color AS destination_account_color,
    NULL AS category_id,
    NULL AS category_name,
    NULL AS category_nature,
    NULL AS category_icon,
    NULL AS category_color,
    NULL AS trip_id,
    NULL AS trip_name,
    NULL AS trip_color,
    NULL AS tag_id,
    NULL AS tag_name,
    NULL AS template_id,
    contribution.name,
    NULL AS payee,
    contribution.notes,
    0 AS is_one_time,
    contribution.created_at,
    contribution.updated_at,
    contribution.archived_at,
    NULL AS refunds_expense_id,
    NULL AS refunds_expense_name,
    0 AS refunds_expense_archived,
    contributor.name AS paid_by_person_name,
    0 AS is_shared,
    CASE contribution.contributor_kind WHEN 'user' THEN 'owner' ELSE 'person' END AS financing_kind,
    contribution.person_id AS payer_id,
    NULL AS settlement_direction,
    NULL AS settlement_person_name,
    -1 AS user_share_cents,
    0 AS is_recurring,
    contribution.direction AS contribution_direction
FROM account_contributions contribution
JOIN accounts shared_account
    ON shared_account.id = contribution.shared_account_id
LEFT JOIN accounts source_account
    ON source_account.id = contribution.source_account_id
LEFT JOIN people contributor
    ON contributor.id = contribution.person_id
WHERE contribution.archived_at IS NULL;
-- @statement
CREATE VIEW v_account_flow AS
SELECT
    account_id,
    date,
    id AS movement_id,
    CASE type
        WHEN 'income' THEN amount_cents
        WHEN 'refund' THEN amount_cents
        WHEN 'expense' THEN -amount_cents
        WHEN 'transfer' THEN -amount_cents
        WHEN 'settlement' THEN CASE settlement_direction
            WHEN 'person_to_user' THEN amount_cents
            WHEN 'user_to_person' THEN -amount_cents
        END
    END AS delta_cents
FROM movements
WHERE archived_at IS NULL

UNION ALL

SELECT
    dest_account_id AS account_id,
    date,
    id AS movement_id,
    amount_cents AS delta_cents
FROM movements
WHERE type = 'transfer'
  AND archived_at IS NULL

UNION ALL

SELECT
    shared_account_id AS account_id,
    date,
    id AS movement_id,
    CASE direction WHEN 'in' THEN amount_cents ELSE -amount_cents END AS delta_cents
FROM account_contributions
WHERE archived_at IS NULL

UNION ALL

SELECT
    source_account_id AS account_id,
    date,
    id AS movement_id,
    CASE direction WHEN 'in' THEN -amount_cents ELSE amount_cents END AS delta_cents
FROM account_contributions
WHERE source_account_id IS NOT NULL
  AND archived_at IS NULL;
-- @statement
CREATE VIEW v_account_balance AS
SELECT
    a.id AS account_id,
    a.starting_balance_cents
        + COALESCE((
            SELECT SUM(f.delta_cents)
            FROM v_account_flow f
            WHERE f.account_id = a.id
        ), 0) AS current_balance_cents
FROM accounts a;
-- @statement
CREATE VIEW v_account_value AS
SELECT
    b.account_id,
    b.current_balance_cents AS physical_balance_cents,
    CASE a.ownership_kind
        WHEN 'shared' THEN COALESCE((
            SELECT am.ownership_basis_points
            FROM account_members am
            WHERE am.account_id = a.id
              AND am.participant_kind = 'user'
              AND am.archived_at IS NULL
            LIMIT 1
        ), 0)
        ELSE 10000
    END AS owner_ownership_basis_points,
    CASE
        WHEN b.current_balance_cents < 0 THEN -(
            ((-b.current_balance_cents) * CASE a.ownership_kind
                WHEN 'shared' THEN COALESCE((
                    SELECT am.ownership_basis_points
                    FROM account_members am
                    WHERE am.account_id = a.id
                      AND am.participant_kind = 'user'
                      AND am.archived_at IS NULL
                    LIMIT 1
                ), 0)
                ELSE 10000
            END + 5000) / 10000
        )
        ELSE (
            (b.current_balance_cents * CASE a.ownership_kind
                WHEN 'shared' THEN COALESCE((
                    SELECT am.ownership_basis_points
                    FROM account_members am
                    WHERE am.account_id = a.id
                      AND am.participant_kind = 'user'
                      AND am.archived_at IS NULL
                    LIMIT 1
                ), 0)
                ELSE 10000
            END + 5000) / 10000
        )
    END AS owner_value_cents
FROM accounts a
JOIN v_account_balance b
    ON b.account_id = a.id;
-- @statement
CREATE VIEW v_actual_expense AS
SELECT
    m.id AS source_id,
    m.date,
    m.category_id,
    m.trip_id,
    m.tag_id,
    CASE
        WHEN s.id IS NULL THEN m.amount_cents
        ELSE COALESCE((
            SELECT sl.owed_amount_cents
            FROM split_lines sl
            WHERE sl.split_id = s.id
              AND sl.participant_kind = 'user'
              AND sl.archived_at IS NULL
        ), 0)
    END AS amount_cents,
    m.is_one_time
FROM movements m
LEFT JOIN splits s
    ON s.movement_id = m.id
   AND s.archived_at IS NULL
WHERE m.type = 'expense'
  AND m.archived_at IS NULL

UNION ALL

SELECT
    m.id AS source_id,
    m.date,
    e.category_id,
    e.trip_id,
    e.tag_id,
    -COALESCE(m.actual_refund_cents, m.amount_cents) AS amount_cents,
    e.is_one_time
FROM movements m
JOIN movements e
    ON e.id = m.refunds_expense_id
WHERE m.type = 'refund'
  AND m.archived_at IS NULL
  AND e.archived_at IS NULL

UNION ALL

SELECT
    s.id AS source_id,
    s.date,
    s.category_id,
    s.trip_id,
    s.tag_id,
    COALESCE((
        SELECT sl.owed_amount_cents
        FROM split_lines sl
        WHERE sl.split_id = s.id
          AND sl.participant_kind = 'user'
          AND sl.archived_at IS NULL
    ), 0) AS amount_cents,
    0 AS is_one_time
FROM splits s
WHERE s.payer_person_id IS NOT NULL
  AND s.movement_id IS NULL
  AND s.archived_at IS NULL;
-- @statement
CREATE VIEW v_actual_income AS
SELECT
    m.id AS source_id,
    m.date,
    m.category_id,
    m.trip_id,
    CASE
        WHEN s.id IS NULL THEN m.amount_cents
        ELSE COALESCE((
            SELECT sl.owed_amount_cents
            FROM split_lines sl
            WHERE sl.split_id = s.id
              AND sl.participant_kind = 'user'
              AND sl.archived_at IS NULL
        ), 0)
    END AS amount_cents
FROM movements m
LEFT JOIN splits s
    ON s.movement_id = m.id
   AND s.archived_at IS NULL
WHERE m.type = 'income'
  AND m.archived_at IS NULL;
-- @statement
CREATE VIEW v_person_balance AS
SELECT
    p.id AS person_id,
    COALESCE((
        SELECT SUM(sl.owed_amount_cents)
        FROM split_lines sl
        JOIN splits s
            ON s.id = sl.split_id
        JOIN movements m
            ON m.id = s.movement_id
        WHERE sl.person_id = p.id
          AND sl.participant_kind = 'person'
          AND s.payer_person_id IS NULL
          AND m.type = 'expense'
          AND COALESCE(m.expense_funding, 'owner') = 'owner'
          AND m.archived_at IS NULL
          AND s.archived_at IS NULL
          AND sl.archived_at IS NULL
    ), 0)
    - COALESCE((
        SELECT SUM(sl.owed_amount_cents)
        FROM split_lines sl
        JOIN splits s
            ON s.id = sl.split_id
        WHERE s.payer_person_id = p.id
          AND sl.participant_kind = 'user'
          AND s.archived_at IS NULL
          AND sl.archived_at IS NULL
    ), 0)
    - COALESCE((
        SELECT SUM(m.amount_cents)
        FROM movements m
        WHERE m.type = 'settlement'
          AND m.person_id = p.id
          AND m.settlement_direction = 'person_to_user'
          AND m.archived_at IS NULL
    ), 0)
    + COALESCE((
        SELECT SUM(m.amount_cents)
        FROM movements m
        WHERE m.type = 'settlement'
          AND m.person_id = p.id
          AND m.settlement_direction = 'user_to_person'
          AND m.archived_at IS NULL
    ), 0) AS balance_cents
FROM people p;
-- @statement
CREATE VIEW v_trip_actual_total AS
SELECT
    trip_id,
    SUM(amount_cents) AS total_actual_cents
FROM v_actual_expense
WHERE trip_id IS NOT NULL
GROUP BY trip_id;
-- @statement
CREATE VIEW v_goal_allocation AS
SELECT
    g.id AS goal_id,
    COALESCE((
        SELECT SUM(ga.amount_cents)
        FROM goal_allocations ga
        WHERE ga.goal_id = g.id
          AND ga.archived_at IS NULL
    ), 0) AS allocated_cents
FROM goals g;
-- @statement
CREATE VIEW v_goal_progress AS
SELECT
    goal_id,
    target_amount_cents,
    saved_cents,
    MAX(target_amount_cents - saved_cents, 0) AS remaining_cents
FROM (
    SELECT
        g.id AS goal_id,
        g.target_amount_cents AS target_amount_cents,
        CASE g.funding_mode
            WHEN 'dedicated_account' THEN COALESCE((
                SELECT b.owner_value_cents
                FROM v_account_value b
                WHERE b.account_id = g.account_id
            ), 0)
            ELSE COALESCE((
                SELECT a.allocated_cents
                FROM v_goal_allocation a
                WHERE a.goal_id = g.id
            ), 0)
        END AS saved_cents
    FROM goals g
);
-- @statement
CREATE VIEW v_account_allocation AS
SELECT
    a.id AS account_id,
    b.owner_value_cents AS balance_cents,
    COALESCE(alloc.allocated_cents, 0) AS allocated_cents,
    b.owner_value_cents - COALESCE(alloc.allocated_cents, 0) AS unallocated_cents
FROM accounts a
JOIN v_account_value b
    ON b.account_id = a.id
LEFT JOIN (
    SELECT
        ga.account_id AS account_id,
        SUM(ga.amount_cents) AS allocated_cents
    FROM goal_allocations ga
    JOIN goals g
        ON g.id = ga.goal_id
    WHERE ga.archived_at IS NULL
      AND g.archived_at IS NULL
      AND g.funding_mode = 'allocations'
    GROUP BY ga.account_id
) alloc
    ON alloc.account_id = a.id;
