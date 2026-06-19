CREATE VIEW v_actual_income AS
SELECT
    m.id AS source_id,
    m.date,
    m.category_id,
    m.trip_id,
    m.amount_cents
FROM movements m
WHERE m.type = 'income'
  AND m.archived_at IS NULL;
