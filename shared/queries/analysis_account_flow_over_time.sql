WITH bucketed_flow AS (
    SELECT
        CASE :bucket
            WHEN 'month' THEN substr(f.date, 1, 7)
            WHEN 'year' THEN substr(f.date, 1, 4)
            ELSE f.date
        END AS bucket,
        f.account_id,
        a.name AS account_name,
        a.display_order AS account_display_order,
        f.delta_cents
    FROM v_account_flow f
    JOIN accounts a
        ON a.id = f.account_id
       AND a.archived_at IS NULL
    WHERE f.date >= :from_date
      AND f.date < :to_date
      AND (:account_id IS NULL OR f.account_id = :account_id)
),
account_totals AS (
    SELECT
        bucket,
        account_id,
        account_name,
        account_display_order,
        SUM(delta_cents) AS delta_cents
    FROM bucketed_flow
    GROUP BY
        bucket,
        account_id,
        account_name,
        account_display_order
),
bucket_totals AS (
    SELECT
        bucket,
        SUM(delta_cents) AS bucket_delta_cents
    FROM bucketed_flow
    GROUP BY bucket
)
SELECT
    account_totals.bucket,
    account_totals.account_id,
    account_totals.account_name,
    account_totals.delta_cents,
    bucket_totals.bucket_delta_cents
FROM account_totals
JOIN bucket_totals
    ON bucket_totals.bucket = account_totals.bucket
WHERE account_totals.delta_cents <> 0
ORDER BY
    account_totals.bucket ASC,
    account_totals.account_display_order ASC,
    lower(account_totals.account_name) ASC;
