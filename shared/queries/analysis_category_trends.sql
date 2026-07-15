-- Net actual expense per category, bucketed over time (day/month/year).
-- Mirrors analysis_actual_by_category (refunds count as negative) but split by bucket,
-- so the UI can draw a trend line per category. Income categories never appear here.
WITH expense_rows AS (
    SELECT
        CAST(CASE WHEN c.id IS NULL THEN NULL ELSE e.category_id END AS TEXT) AS category_id,
        c.name AS category_name,
        c.color AS category_color,
        CASE :bucket
            WHEN 'month' THEN substr(e.date, 1, 7)
            WHEN 'year' THEN substr(e.date, 1, 4)
            ELSE e.date
        END AS bucket,
        e.amount_cents
    FROM v_actual_expense e
    LEFT JOIN categories c
        ON c.id = e.category_id
       AND c.archived_at IS NULL
    LEFT JOIN movements m
        ON m.id = e.source_id
    WHERE e.date >= :from_date
      AND e.date < :to_date
      AND (
          :one_time_mode = 'include'
          OR (:one_time_mode = 'exclude' AND e.is_one_time = 0)
          OR (:one_time_mode = 'only' AND e.is_one_time = 1)
      )
      AND (:category_nature IS NULL OR c.nature = :category_nature)
      AND (:account_id IS NULL OR m.account_id = :account_id)
      AND (:category_id IS NULL
           OR e.category_id = :category_id
           OR e.category_id IN (SELECT id FROM categories
                                WHERE parent_id = :category_id AND archived_at IS NULL))
)
SELECT
    category_id,
    category_name,
    category_color,
    bucket,
    SUM(amount_cents) AS expense_cents
FROM expense_rows
GROUP BY category_id, category_name, category_color, bucket
HAVING SUM(amount_cents) <> 0
ORDER BY bucket ASC, expense_cents DESC;
