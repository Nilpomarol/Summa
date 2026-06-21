-- Top merchants / most frequent actual expenses in a period, grouped by payee
-- (falling back to the movement name). Rows with no label collapse into one NULL group.
WITH merchant_rows AS (
    SELECT
        COALESCE(NULLIF(m.payee, ''), NULLIF(m.name, '')) AS merchant_label,
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
)
SELECT
    merchant_label,
    SUM(amount_cents) AS total_cents,
    COUNT(*) AS movement_count
FROM merchant_rows
GROUP BY merchant_label
ORDER BY total_cents DESC, movement_count DESC, lower(COALESCE(merchant_label, '')) ASC
LIMIT :limit;
