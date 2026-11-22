-- Gestor finances v21 -> v22 migration.
-- The monthly plan keeps what each month had: one row per monthly budget and the month its values
-- apply from, a NULL limit meaning it left the plan that month. The plan kept no history before,
-- so every monthly budget in use applies to all the months so far. A budget whose category has been
-- archived is not in use: the plan already leaves it out.

CREATE TABLE budget_versions (
    budget_id                      TEXT    NOT NULL REFERENCES budgets(id),
    from_month                     TEXT    NOT NULL CHECK (from_month GLOB '[0-9][0-9][0-9][0-9]-[0-1][0-9]'),
    category_id                    TEXT    REFERENCES categories(id),
    limit_amount_cents             INTEGER CHECK (limit_amount_cents IS NULL OR limit_amount_cents > 0),
    include_trip_expenses          INTEGER NOT NULL DEFAULT 1 CHECK (include_trip_expenses IN (0,1)),
    include_extraordinary_expenses INTEGER NOT NULL DEFAULT 1 CHECK (include_extraordinary_expenses IN (0,1)),

    PRIMARY KEY (budget_id, from_month)
);

INSERT INTO budget_versions(
    budget_id,
    from_month,
    category_id,
    limit_amount_cents,
    include_trip_expenses,
    include_extraordinary_expenses
)
SELECT
    id,
    '0001-01',
    category_id,
    limit_amount_cents,
    include_trip_expenses,
    include_extraordinary_expenses
FROM budgets
WHERE archived_at IS NULL
  AND period = 'monthly'
  AND (category_id IS NULL OR category_id IN (SELECT id FROM categories WHERE archived_at IS NULL));

UPDATE meta SET value = '22' WHERE key = 'schema_version';
