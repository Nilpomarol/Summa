-- Total actual expense per weekday (0 = Sunday … 6 = Saturday), net of refunds.
-- Feeds the day-of-week radar; works for any span (month/year/all-time) because it
-- aggregates straight from v_actual_expense rather than re-bucketing daily rows.
SELECT
    CAST(strftime('%w', e.date) AS INTEGER) AS weekday,
    SUM(e.amount_cents) AS expense_cents
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
GROUP BY weekday
HAVING SUM(e.amount_cents) <> 0
ORDER BY weekday ASC;
