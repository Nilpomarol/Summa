-- Largest individual actual expenses in a period.
-- Refund/credit rows (amount_cents <= 0) are excluded; ordered by amount descending.
SELECT
    e.source_id,
    e.date,
    COALESCE(NULLIF(m.payee, ''), NULLIF(m.name, '')) AS label,
    CAST(CASE WHEN c.id IS NULL THEN NULL ELSE e.category_id END AS TEXT) AS category_id,
    c.name AS category_name,
    c.icon AS category_icon,
    c.color AS category_color,
    e.amount_cents
FROM v_actual_expense e
LEFT JOIN movements m
    ON m.id = e.source_id
LEFT JOIN categories c
    ON c.id = e.category_id
   AND c.archived_at IS NULL
WHERE e.date >= :from_date
  AND e.date < :to_date
  AND e.amount_cents > 0
  AND (
      :one_time_mode = 'include'
      OR (:one_time_mode = 'exclude' AND e.is_one_time = 0)
      OR (:one_time_mode = 'only' AND e.is_one_time = 1)
  )
  AND (:category_nature IS NULL OR c.nature = :category_nature)
ORDER BY e.amount_cents DESC, e.date DESC
LIMIT :limit;
