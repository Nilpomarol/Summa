WITH actual_rows AS (
    SELECT
        category_id,
        trip_id,
        amount_cents AS expense_cents,
        0 AS income_cents,
        is_one_time
    FROM v_actual_expense
    WHERE date >= :from_date
      AND date < :to_date

    UNION ALL

    SELECT
        category_id,
        trip_id,
        0 AS expense_cents,
        amount_cents AS income_cents,
        0 AS is_one_time
    FROM v_actual_income
    WHERE date >= :from_date
      AND date < :to_date
),
active_groups AS (
    SELECT
        CASE
            WHEN :group_trips = 1 AND t.id IS NOT NULL THEN 'trip'
            ELSE 'category'
        END AS row_kind,
        CASE
            WHEN :group_trips = 1 AND t.id IS NOT NULL THEN NULL
            ELSE r.category_id
        END AS category_id,
        CASE
            WHEN :group_trips = 1 AND t.id IS NOT NULL THEN t.id
            ELSE NULL
        END AS trip_id,
        r.expense_cents,
        r.income_cents,
        c.name  AS category_name,
        c.kind  AS category_kind,
        c.nature AS category_nature,
        c.icon  AS category_icon,
        c.color AS category_color,
        t.name  AS trip_name
    FROM actual_rows r
    LEFT JOIN categories c
        ON c.id = r.category_id
       AND c.archived_at IS NULL
    LEFT JOIN trips t
        ON t.id = r.trip_id
       AND t.archived_at IS NULL
    WHERE (
        (r.expense_cents <> 0 AND (
            :one_time_mode = 'include'
            OR (:one_time_mode = 'exclude' AND r.is_one_time = 0)
            OR (:one_time_mode = 'only' AND r.is_one_time = 1)
        ))
        OR (r.income_cents > 0 AND :one_time_mode != 'only')
    )
    AND (:category_nature IS NULL OR c.nature = :category_nature)
)
SELECT
    ag.row_kind,
    CAST(ag.category_id AS TEXT) AS category_id,
    MAX(ag.category_name) AS category_name,
    MAX(ag.category_kind) AS category_kind,
    MAX(ag.category_nature) AS category_nature,
    MAX(ag.category_icon) AS category_icon,
    MAX(ag.category_color) AS category_color,
    CAST(ag.trip_id AS TEXT) AS trip_id,
    MAX(ag.trip_name) AS trip_name,
    SUM(ag.expense_cents) AS expense_cents,
    SUM(ag.income_cents) AS income_cents,
    SUM(ag.income_cents) - SUM(ag.expense_cents) AS net_cents
FROM active_groups ag
GROUP BY
    ag.row_kind,
    ag.category_id,
    ag.trip_id
HAVING SUM(ag.expense_cents) <> 0
    OR SUM(ag.income_cents) <> 0
ORDER BY
    ABS(SUM(ag.expense_cents)) + ABS(SUM(ag.income_cents)) DESC,
    lower(COALESCE(MAX(ag.trip_name), MAX(ag.category_name), '')) ASC;
