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
