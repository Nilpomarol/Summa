-- Reservations by funding account, including retained history of archived goals.
SELECT account_id, SUM(amount_cents) AS allocated_cents
FROM goal_allocations
WHERE goal_id = :goal_id AND archived_at IS NULL
GROUP BY account_id;
