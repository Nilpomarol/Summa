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
