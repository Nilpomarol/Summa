WITH actual_rows AS (
    SELECT
        e.date,
        e.amount_cents AS expense_cents,
        0 AS income_cents
    FROM v_actual_expense e
    LEFT JOIN categories c
        ON c.id = e.category_id
       AND c.archived_at IS NULL
    WHERE e.date >= :from_date
      AND e.date < :to_date
      AND (
          :one_time_mode = 'include'
          OR (:one_time_mode = 'exclude' AND e.is_one_time = 0)
          OR (:one_time_mode = 'only' AND e.is_one_time = 1)
      )
      AND (:category_nature IS NULL OR c.nature = :category_nature)

    UNION ALL

    SELECT
        i.date,
        0 AS expense_cents,
        i.amount_cents AS income_cents
    FROM v_actual_income i
    LEFT JOIN categories c
        ON c.id = i.category_id
       AND c.archived_at IS NULL
    WHERE i.date >= :from_date
      AND i.date < :to_date
      AND :one_time_mode != 'only'
      AND (:category_nature IS NULL OR c.nature = :category_nature)
),
bucketed_actual AS (
    SELECT
        CASE :bucket
            WHEN 'month' THEN substr(date, 1, 7)
            WHEN 'year' THEN substr(date, 1, 4)
            ELSE date
        END AS bucket,
        expense_cents,
        income_cents
    FROM actual_rows
)
SELECT
    bucket,
    SUM(income_cents) AS income_cents,
    SUM(expense_cents) AS expense_cents,
    SUM(income_cents) - SUM(expense_cents) AS net_cents,
    CASE
        WHEN SUM(income_cents) > 0
            THEN ((SUM(income_cents) - SUM(expense_cents)) * 10000) / SUM(income_cents)
        ELSE 0
    END AS savings_rate_basis_points
FROM bucketed_actual
GROUP BY bucket
HAVING SUM(income_cents) <> 0
    OR SUM(expense_cents) <> 0
ORDER BY bucket ASC;
