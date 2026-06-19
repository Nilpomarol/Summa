CREATE VIEW v_actual_expense AS
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
    m.category_id,
    m.trip_id,
    -COALESCE(m.actual_refund_cents, m.amount_cents) AS amount_cents,
    COALESCE((
        SELECT e.is_one_time
        FROM movements e
        WHERE e.id = m.refunds_expense_id
    ), 0) AS is_one_time
FROM movements m
WHERE m.type = 'refund'
  AND m.archived_at IS NULL

UNION ALL

SELECT
    s.id AS source_id,
    s.date,
    s.category_id,
    s.trip_id,
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
