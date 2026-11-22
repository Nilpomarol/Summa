CREATE VIEW v_trip_actual_total AS
SELECT
    trip_id,
    SUM(amount_cents) AS total_actual_cents
FROM v_actual_expense
WHERE trip_id IS NOT NULL
GROUP BY trip_id;
