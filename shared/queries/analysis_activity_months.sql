-- Distinct calendar months containing active ledger or external-split activity.
SELECT month
FROM (
    SELECT substr(date, 1, 7) AS month
    FROM movements
    WHERE archived_at IS NULL

    UNION

    SELECT substr(date, 1, 7) AS month
    FROM splits
    WHERE movement_id IS NULL
      AND payer_person_id IS NOT NULL
      AND archived_at IS NULL
)
WHERE length(month) = 7
ORDER BY month DESC;
