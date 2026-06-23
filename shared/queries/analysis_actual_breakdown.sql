WITH actual_rows AS (
    SELECT
        CASE
            WHEN :group_trips = 1 AND t.id IS NOT NULL THEN 'trip'
            ELSE 'category'
        END AS row_kind,
        CASE
            WHEN :group_trips = 1 AND t.id IS NOT NULL THEN NULL
            ELSE e.category_id
        END AS category_id,
        CASE
            WHEN :group_trips = 1 AND t.id IS NOT NULL THEN t.id
            ELSE NULL
        END AS trip_id,
        e.amount_cents AS expense_cents,
        0 AS income_cents
    FROM v_actual_expense e
    LEFT JOIN categories c
        ON c.id = e.category_id
       AND c.archived_at IS NULL
    LEFT JOIN trips t
        ON t.id = e.trip_id
       AND t.archived_at IS NULL
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
        CASE
            WHEN :group_trips = 1 AND t.id IS NOT NULL THEN 'trip'
            ELSE 'category'
        END AS row_kind,
        CASE
            WHEN :group_trips = 1 AND t.id IS NOT NULL THEN NULL
            ELSE i.category_id
        END AS category_id,
        CASE
            WHEN :group_trips = 1 AND t.id IS NOT NULL THEN t.id
            ELSE NULL
        END AS trip_id,
        0 AS expense_cents,
        i.amount_cents AS income_cents
    FROM v_actual_income i
    LEFT JOIN categories c
        ON c.id = i.category_id
       AND c.archived_at IS NULL
    LEFT JOIN trips t
        ON t.id = i.trip_id
       AND t.archived_at IS NULL
    WHERE i.date >= :from_date
      AND i.date < :to_date
      AND :one_time_mode != 'only'
      AND (:category_nature IS NULL OR c.nature = :category_nature)
),
active_groups AS (
    SELECT
        r.row_kind,
        CAST(CASE WHEN c.id IS NULL THEN NULL ELSE r.category_id END AS TEXT) AS category_id,
        c.name AS category_name,
        c.kind AS category_kind,
        c.nature AS category_nature,
        c.icon AS category_icon,
        c.color AS category_color,
        CAST(CASE WHEN t.id IS NULL THEN NULL ELSE r.trip_id END AS TEXT) AS trip_id,
        t.name AS trip_name,
        r.expense_cents,
        r.income_cents
    FROM actual_rows r
    LEFT JOIN categories c
        ON c.id = r.category_id
       AND c.archived_at IS NULL
    LEFT JOIN trips t
        ON t.id = r.trip_id
       AND t.archived_at IS NULL
)
SELECT
    row_kind,
    category_id,
    category_name,
    category_kind,
    category_nature,
    category_icon,
    category_color,
    trip_id,
    trip_name,
    SUM(expense_cents) AS expense_cents,
    SUM(income_cents) AS income_cents,
    SUM(income_cents) - SUM(expense_cents) AS net_cents
FROM active_groups
GROUP BY
    row_kind,
    category_id,
    category_name,
    category_kind,
    category_nature,
    category_icon,
    category_color,
    trip_id,
    trip_name
HAVING SUM(expense_cents) <> 0
    OR SUM(income_cents) <> 0
ORDER BY
    ABS(SUM(expense_cents)) + ABS(SUM(income_cents)) DESC,
    lower(COALESCE(trip_name, category_name, '')) ASC;
