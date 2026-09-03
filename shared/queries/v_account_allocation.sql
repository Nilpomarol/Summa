CREATE VIEW v_account_allocation AS
SELECT
    a.id AS account_id,
    b.current_balance_cents AS balance_cents,
    COALESCE(alloc.allocated_cents, 0) AS allocated_cents,
    b.current_balance_cents - COALESCE(alloc.allocated_cents, 0) AS unallocated_cents
FROM accounts a
JOIN v_account_balance b
    ON b.account_id = a.id
LEFT JOIN (
    SELECT
        ga.account_id AS account_id,
        SUM(ga.amount_cents) AS allocated_cents
    FROM goal_allocations ga
    JOIN goals g
        ON g.id = ga.goal_id
    WHERE ga.archived_at IS NULL
      AND g.archived_at IS NULL
      AND g.funding_mode = 'allocations'
    GROUP BY ga.account_id
) alloc
    ON alloc.account_id = a.id;
