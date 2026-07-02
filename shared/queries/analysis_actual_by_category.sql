WITH actual_rows AS (
    SELECT
        e.category_id,
        e.amount_cents AS expense_cents,
        0 AS income_cents
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
      AND (:category_id IS NULL OR e.category_id = :category_id)

    UNION ALL

    SELECT
        i.category_id,
        0 AS expense_cents,
        i.amount_cents AS income_cents
    FROM v_actual_income i
    LEFT JOIN categories c
        ON c.id = i.category_id
       AND c.archived_at IS NULL
    LEFT JOIN movements m
        ON m.id = i.source_id
    WHERE i.date >= :from_date
      AND i.date < :to_date
      AND :one_time_mode != 'only'
      AND (:category_nature IS NULL OR c.nature = :category_nature)
      AND (:account_id IS NULL OR m.account_id = :account_id)
      AND (:category_id IS NULL OR i.category_id = :category_id)
),
active_categories AS (
    SELECT
        CAST(CASE WHEN c.id IS NULL THEN NULL ELSE r.category_id END AS TEXT) AS category_id,
        c.name AS category_name,
        c.kind AS category_kind,
        c.nature AS category_nature,
        c.icon AS category_icon,
        c.color AS category_color,
        r.expense_cents,
        r.income_cents
    FROM actual_rows r
    LEFT JOIN categories c
        ON c.id = r.category_id
       AND c.archived_at IS NULL
)
SELECT
    category_id,
    category_name,
    category_kind,
    category_nature,
    category_icon,
    category_color,
    SUM(expense_cents) AS expense_cents,
    SUM(income_cents) AS income_cents,
    SUM(income_cents) - SUM(expense_cents) AS net_cents
FROM active_categories
GROUP BY
    category_id,
    category_name,
    category_kind,
    category_nature,
    category_icon,
    category_color
HAVING SUM(expense_cents) <> 0
    OR SUM(income_cents) <> 0
ORDER BY
    ABS(SUM(expense_cents)) + ABS(SUM(income_cents)) DESC,
    lower(COALESCE(category_name, '')) ASC;
