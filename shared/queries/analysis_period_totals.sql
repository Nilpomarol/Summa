WITH actual_expense AS (
    SELECT COALESCE(SUM(e.amount_cents), 0) AS expense_cents
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
),
actual_income AS (
    SELECT COALESCE(SUM(i.amount_cents), 0) AS income_cents
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
      AND (:category_id IS NULL
           OR i.category_id = :category_id
           OR i.category_id IN (SELECT id FROM categories
                                WHERE parent_id = :category_id AND archived_at IS NULL))
),
period_flow AS (
    SELECT COALESCE(SUM(f.delta_cents), 0) AS flow_cents
    FROM v_account_flow f
    JOIN accounts a
        ON a.id = f.account_id
       AND a.archived_at IS NULL
    WHERE f.date >= :from_date
      AND f.date < :to_date
),
net_worth AS (
    SELECT COALESCE(SUM(b.current_balance_cents), 0) AS net_worth_cents
    FROM v_account_balance b
    JOIN accounts a
        ON a.id = b.account_id
       AND a.archived_at IS NULL
)
SELECT
    net_worth.net_worth_cents,
    actual_income.income_cents AS actual_income_cents,
    actual_expense.expense_cents AS actual_expense_cents,
    actual_income.income_cents - actual_expense.expense_cents AS net_actual_cents,
    period_flow.flow_cents AS account_flow_cents,
    CASE
        WHEN actual_income.income_cents > 0
            THEN ((actual_income.income_cents - actual_expense.expense_cents) * 10000) / actual_income.income_cents
        ELSE 0
    END AS savings_rate_basis_points
FROM net_worth, actual_income, actual_expense, period_flow;
