CREATE VIEW v_goal_progress AS
SELECT
    goal_id,
    target_amount_cents,
    saved_cents,
    MAX(target_amount_cents - saved_cents, 0) AS remaining_cents
FROM (
    SELECT
        g.id AS goal_id,
        g.target_amount_cents AS target_amount_cents,
        CASE g.funding_mode
            WHEN 'dedicated_account' THEN COALESCE((
                SELECT b.owner_value_cents
                FROM v_account_value b
                WHERE b.account_id = g.account_id
            ), 0)
            ELSE COALESCE((
                SELECT a.allocated_cents
                FROM v_goal_allocation a
                WHERE a.goal_id = g.id
            ), 0)
        END AS saved_cents
    FROM goals g
);
