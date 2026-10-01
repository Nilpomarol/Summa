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
