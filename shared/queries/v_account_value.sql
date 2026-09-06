CREATE VIEW v_account_value AS
SELECT
    b.account_id,
    b.current_balance_cents AS physical_balance_cents,
    CASE a.ownership_kind
        WHEN 'shared' THEN COALESCE((
            SELECT am.ownership_basis_points
            FROM account_members am
            WHERE am.account_id = a.id
              AND am.participant_kind = 'user'
              AND am.archived_at IS NULL
            LIMIT 1
        ), 0)
        ELSE 10000
    END AS owner_ownership_basis_points,
    CASE
        WHEN b.current_balance_cents < 0 THEN -(
            ((-b.current_balance_cents) * CASE a.ownership_kind
                WHEN 'shared' THEN COALESCE((
                    SELECT am.ownership_basis_points
                    FROM account_members am
                    WHERE am.account_id = a.id
                      AND am.participant_kind = 'user'
                      AND am.archived_at IS NULL
                    LIMIT 1
                ), 0)
                ELSE 10000
            END + 5000) / 10000
        )
        ELSE (
            (b.current_balance_cents * CASE a.ownership_kind
                WHEN 'shared' THEN COALESCE((
                    SELECT am.ownership_basis_points
                    FROM account_members am
                    WHERE am.account_id = a.id
                      AND am.participant_kind = 'user'
                      AND am.archived_at IS NULL
                    LIMIT 1
                ), 0)
                ELSE 10000
            END + 5000) / 10000
        )
    END AS owner_value_cents
FROM accounts a
JOIN v_account_balance b
    ON b.account_id = a.id;
