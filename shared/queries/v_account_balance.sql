CREATE VIEW v_account_balance AS
SELECT
    a.id AS account_id,
    a.starting_balance_cents
        + COALESCE((
            SELECT SUM(f.delta_cents)
            FROM v_account_flow f
            WHERE f.account_id = a.id
        ), 0) AS current_balance_cents
FROM accounts a;
