-- Per-category actual expense frequency vs volume: how many expense movements occurred
-- and how much they total in the period. Refund/credit rows (amount_cents <= 0) are
-- excluded so the count reflects real purchases. Feeds the frequency-vs-volume scatter.
WITH expense_rows AS (
    SELECT
        CAST(CASE WHEN c.id IS NULL THEN NULL ELSE e.category_id END AS TEXT) AS category_id,
        c.name AS category_name,
        c.color AS category_color,
        e.amount_cents
    FROM v_actual_expense e
    LEFT JOIN categories c
        ON c.id = e.category_id
       AND c.archived_at IS NULL
    LEFT JOIN movements m
        ON m.id = e.source_id
    WHERE e.date >= :from_date
      AND e.date < :to_date
      AND e.amount_cents > 0
      AND (
          :one_time_mode = 'include'
          OR (:one_time_mode = 'exclude' AND e.is_one_time = 0)
          OR (:one_time_mode = 'only' AND e.is_one_time = 1)
      )
      AND (:category_nature IS NULL OR c.nature = :category_nature)
      AND (:account_id IS NULL OR m.account_id = :account_id)
      AND (:category_id IS NULL OR e.category_id = :category_id)
)
SELECT
    category_id,
    category_name,
    category_color,
    COUNT(*) AS movement_count,
    SUM(amount_cents) AS total_cents
FROM expense_rows
GROUP BY category_id, category_name, category_color
HAVING SUM(amount_cents) <> 0
ORDER BY total_cents DESC;
