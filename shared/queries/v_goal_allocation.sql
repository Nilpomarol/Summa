CREATE VIEW v_goal_allocation AS
SELECT
    g.id AS goal_id,
    COALESCE((
        SELECT SUM(ga.amount_cents)
        FROM goal_allocations ga
        WHERE ga.goal_id = g.id
          AND ga.archived_at IS NULL
    ), 0) AS allocated_cents
FROM goals g;
