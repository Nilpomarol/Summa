-- Cumulative net worth at the end of each bucket within a period (day/month/year).
-- Net worth is derived: active accounts' starting balances + all account flow up to the
-- bucket end. The opening value folds in flow that happened before :from_date so the line
-- starts from the true running balance, not zero. Only buckets with activity are returned.
WITH bucket_flow AS (
    SELECT
        CASE :bucket
            WHEN 'month' THEN substr(f.date, 1, 7)
            WHEN 'year' THEN substr(f.date, 1, 4)
            ELSE f.date
        END AS bucket,
        SUM(f.delta_cents) AS delta_cents
    FROM v_account_flow f
    JOIN accounts a
        ON a.id = f.account_id
       AND a.archived_at IS NULL
    WHERE f.date >= :from_date
      AND f.date < :to_date
    GROUP BY bucket
),
opening AS (
    SELECT
        COALESCE((
            SELECT SUM(starting_balance_cents)
            FROM accounts
            WHERE archived_at IS NULL
        ), 0)
        + COALESCE((
            SELECT SUM(f.delta_cents)
            FROM v_account_flow f
            JOIN accounts a
                ON a.id = f.account_id
               AND a.archived_at IS NULL
            WHERE f.date < :from_date
        ), 0) AS opening_cents
)
SELECT
    bf.bucket,
    CAST(
        (SELECT opening_cents FROM opening)
        + COALESCE((
            SELECT SUM(prior.delta_cents)
            FROM bucket_flow prior
            WHERE prior.bucket <= bf.bucket
        ), 0)
        AS INTEGER
    ) AS net_worth_cents
FROM bucket_flow bf
ORDER BY bf.bucket ASC;
