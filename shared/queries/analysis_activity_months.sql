-- Distinct calendar months containing active ledger activity.
SELECT month
FROM (
    SELECT substr(date, 1, 7) AS month
    FROM movements
    WHERE archived_at IS NULL
)
WHERE length(month) = 7
GROUP BY month
ORDER BY month DESC;
