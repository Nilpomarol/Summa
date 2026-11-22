-- Summa v19 -> v20 migration.
-- An expense another person paid becomes an ordinary expense row: `payer_person_id` names the
-- payer, `account_id` is NULL because none of the owner's money moved, and its split's owner line
-- is what the owner owes that person. Until now it lived only as a standalone `splits` row.
--
-- `movements` is rebuilt because `account_id` loses NOT NULL, and `splits` is rebuilt because its
-- standalone columns go. `movements`, `splits`, and `split_lines` reference each other and foreign
-- keys are enforced here, so a DROP TABLE of a referenced table would cascade into the rows being
-- kept. The new tables are therefore built alongside under `_new` names that reference each other,
-- filled, checked, and renamed into place only after the old tables have been emptied of every
-- cross-reference and dropped leaf first. Renaming rewrites the foreign keys that name the `_new`
-- tables. Every statement is valid on its own, so no deferred check is relied on.
--
-- Each standalone split becomes a movement with the split's own id, so nothing that remembers that
-- id loses it, and the split itself is kept as that movement's split with its lines unchanged.
-- Semicolons are deliberately kept out of comments so naive statement splitters cannot mis-cut.

DROP VIEW IF EXISTS v_account_allocation;
DROP VIEW IF EXISTS v_goal_progress;
DROP VIEW IF EXISTS v_goal_allocation;
DROP VIEW IF EXISTS v_trip_actual_total;
DROP VIEW IF EXISTS v_person_balance;
DROP VIEW IF EXISTS v_actual_income;
DROP VIEW IF EXISTS v_actual_expense;
DROP VIEW IF EXISTS v_account_value;
DROP VIEW IF EXISTS v_account_balance;
DROP VIEW IF EXISTS v_account_flow;
DROP VIEW IF EXISTS v_movement_summary;
DROP VIEW IF EXISTS v_movement_shared;

DROP TRIGGER IF EXISTS movements_validate_shared_expense_funding_insert;
DROP TRIGGER IF EXISTS movements_validate_shared_expense_funding_update;
DROP TRIGGER IF EXISTS splits_reject_archive_for_active_shared_expense;

CREATE TABLE movements_new (
    id                   TEXT    PRIMARY KEY,
    type                 TEXT    NOT NULL CHECK (type IN ('expense','income','transfer','settlement','refund')),
    amount_cents         INTEGER NOT NULL CHECK (amount_cents > 0),
    date                 TEXT    NOT NULL,
    account_id           TEXT    REFERENCES accounts(id),
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
    refunds_expense_id   TEXT    REFERENCES movements_new(id),
    actual_refund_cents  INTEGER CHECK (actual_refund_cents IS NULL OR actual_refund_cents >= 0),
    expense_funding      TEXT    CHECK (expense_funding IN ('owner','shared_account')),
    shared_split_id      TEXT    REFERENCES splits_new(id) DEFERRABLE INITIALLY DEFERRED,
    payer_person_id      TEXT    REFERENCES people(id),
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
    CHECK ( is_one_time = 0 OR type = 'expense' ),
    CHECK ( (account_id IS NULL) = (payer_person_id IS NOT NULL) ),
    CHECK ( payer_person_id IS NULL
            OR (type = 'expense' AND expense_funding IS NULL AND template_id IS NULL) )
);

CREATE TABLE splits_new (
    id           TEXT    PRIMARY KEY,
    movement_id  TEXT    NOT NULL REFERENCES movements_new(id) ON DELETE CASCADE,
    entry_method TEXT    NOT NULL CHECK (entry_method IN ('equal','exact','percentage')),
    created_at   TEXT    NOT NULL,
    updated_at   TEXT    NOT NULL,
    archived_at  TEXT
);

CREATE TABLE split_lines_new (
    id                TEXT    PRIMARY KEY,
    split_id          TEXT    NOT NULL REFERENCES splits_new(id) ON DELETE CASCADE,
    participant_kind  TEXT    NOT NULL CHECK (participant_kind IN ('user','person')),
    person_id         TEXT    REFERENCES people(id),
    owed_amount_cents INTEGER NOT NULL CHECK (owed_amount_cents >= 0),
    owed_percent      REAL,
    created_at        TEXT    NOT NULL,
    updated_at        TEXT    NOT NULL,
    archived_at       TEXT,

    CHECK ( (participant_kind = 'user') = (person_id IS NULL) )
);

-- The split each shared-account expense names is filled in once the splits exist. A settlement
-- recorded before scopes existed consumed all debt, which migration 011 wrote as 'all'.
INSERT INTO movements_new (
    id, type, amount_cents, date, account_id, dest_account_id, name, payee, notes, is_one_time,
    category_id, tag_id, trip_id, template_id, import_batch_id, person_id, settlement_direction,
    settlement_scope, refunds_expense_id, actual_refund_cents, expense_funding, shared_split_id,
    payer_person_id, created_at, updated_at, archived_at
)
SELECT
    id, type, amount_cents, date, account_id, dest_account_id, name, payee, notes, is_one_time,
    category_id, tag_id, trip_id, template_id, import_batch_id, person_id, settlement_direction,
    COALESCE(settlement_scope, CASE WHEN type = 'settlement' THEN 'all' END),
    refunds_expense_id, actual_refund_cents, expense_funding, NULL,
    NULL, created_at, updated_at, archived_at
FROM movements;

-- A standalone split is an expense the payer made, dated, described, and categorized by the split.
INSERT INTO movements_new (
    id, type, amount_cents, date, account_id, name, is_one_time, category_id, tag_id, trip_id,
    payer_person_id, created_at, updated_at, archived_at
)
SELECT
    id, 'expense', total_amount_cents, date, NULL, description, 0, category_id, tag_id, trip_id,
    payer_person_id, created_at, updated_at, archived_at
FROM splits
WHERE movement_id IS NULL;

INSERT INTO splits_new (id, movement_id, entry_method, created_at, updated_at, archived_at)
SELECT id, COALESCE(movement_id, id), entry_method, created_at, updated_at, archived_at
FROM splits;

INSERT INTO split_lines_new (
    id, split_id, participant_kind, person_id, owed_amount_cents, owed_percent,
    created_at, updated_at, archived_at
)
SELECT
    id, split_id, participant_kind, person_id, owed_amount_cents, owed_percent,
    created_at, updated_at, archived_at
FROM split_lines;

UPDATE movements_new
SET shared_split_id = (
    SELECT old.shared_split_id FROM movements old WHERE old.id = movements_new.id
)
WHERE id IN (SELECT id FROM movements WHERE shared_split_id IS NOT NULL);

-- Stops the migration, before anything is dropped, unless every row arrived.
CREATE TABLE payer_unification_guard (
    is_valid INTEGER NOT NULL CHECK (is_valid = 1)
);

INSERT INTO payer_unification_guard(is_valid)
SELECT
    (SELECT COUNT(*) FROM movements_new)
        = (SELECT COUNT(*) FROM movements) + (SELECT COUNT(*) FROM splits WHERE movement_id IS NULL)
    AND (SELECT COUNT(*) FROM splits_new) = (SELECT COUNT(*) FROM splits)
    AND (SELECT COUNT(*) FROM split_lines_new) = (SELECT COUNT(*) FROM split_lines)
    AND (SELECT COUNT(*) FROM movements_new WHERE shared_split_id IS NOT NULL)
        = (SELECT COUNT(*) FROM movements WHERE shared_split_id IS NOT NULL);

-- Nothing references the old tables once the old split lines are gone and the old movements no
-- longer name a split, so each drop deletes only its own rows.
UPDATE movements SET shared_split_id = NULL WHERE shared_split_id IS NOT NULL;

DROP TABLE split_lines;
DROP TABLE splits;
DROP TABLE movements;

ALTER TABLE movements_new RENAME TO movements;
ALTER TABLE splits_new RENAME TO splits;
ALTER TABLE split_lines_new RENAME TO split_lines;

-- Every foreign key that named a `_new` table must now name its final table.
INSERT INTO payer_unification_guard(is_valid)
SELECT NOT EXISTS (
    SELECT 1 FROM sqlite_master
    WHERE type = 'table'
      AND (instr(sql, 'movements_new') > 0 OR instr(sql, 'splits_new') > 0)
);

DROP TABLE payer_unification_guard;

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

CREATE INDEX idx_movements_payer
    ON movements(payer_person_id)
    WHERE payer_person_id IS NOT NULL;

CREATE INDEX idx_movements_template
    ON movements(template_id)
    WHERE template_id IS NOT NULL;

CREATE INDEX idx_movements_refunds
    ON movements(refunds_expense_id)
    WHERE refunds_expense_id IS NOT NULL;

CREATE INDEX idx_movements_import_batch
    ON movements(import_batch_id)
    WHERE import_batch_id IS NOT NULL;

CREATE UNIQUE INDEX idx_splits_movement
    ON splits(movement_id);

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

CREATE VIEW v_movement_shared AS
SELECT
    m.id AS movement_id,
    m.payer_person_id IS NULL AND EXISTS (
        SELECT 1
        FROM splits s
        WHERE s.movement_id = m.id
          AND s.archived_at IS NULL
    ) AS is_shared
FROM movements m;

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
    COALESCE(payer_people.name, NULLIF((
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
    ), '')) AS paid_by_person_name,
    COALESCE(v_movement_shared.is_shared, 0) AS is_shared,
    CASE
        WHEN movements.payer_person_id IS NOT NULL THEN 'person'
        WHEN movements.type = 'expense' THEN COALESCE(movements.expense_funding, 'owner')
        WHEN movements.type IN ('income','transfer','settlement','refund') THEN 'owner'
        ELSE NULL
    END AS financing_kind,
    movements.payer_person_id AS payer_id,
    movements.settlement_direction,
    settlement_people.name AS settlement_person_name,
    CASE
        WHEN (v_movement_shared.is_shared = 1 AND movements.type IN ('expense', 'income'))
          OR movements.payer_person_id IS NOT NULL
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
LEFT JOIN accounts
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
LEFT JOIN people AS payer_people
    ON payer_people.id = movements.payer_person_id
LEFT JOIN v_movement_shared
    ON v_movement_shared.movement_id = movements.id
LEFT JOIN movements AS refunded_expense
    ON refunded_expense.id = movements.refunds_expense_id
WHERE movements.archived_at IS NULL

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
-- An expense another person paid left none of the owner's accounts.
WHERE account_id IS NOT NULL
  AND archived_at IS NULL

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
  AND e.archived_at IS NULL;

-- An income allocated across the members of a shared account counts only the app owner's line.
-- An income without a split is wholly the app owner's, which is every income by default.
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
          AND m.payer_person_id IS NULL
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
        JOIN movements m
            ON m.id = s.movement_id
        WHERE m.payer_person_id = p.id
          AND sl.participant_kind = 'user'
          AND m.archived_at IS NULL
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

CREATE VIEW v_trip_actual_total AS
SELECT
    trip_id,
    SUM(amount_cents) AS total_actual_cents
FROM v_actual_expense
WHERE trip_id IS NOT NULL
GROUP BY trip_id;

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

UPDATE meta SET value = '20' WHERE key = 'schema_version';
