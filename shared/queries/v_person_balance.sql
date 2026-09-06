CREATE VIEW v_person_balance AS
SELECT
    p.id AS person_id,
    COALESCE((
        SELECT SUM(sl.owed_amount_cents)
        FROM split_lines sl
        JOIN splits s
            ON s.id = sl.split_id
        JOIN movements m
            ON m.id = s.movement_id
        WHERE sl.person_id = p.id
          AND sl.participant_kind = 'person'
          AND s.payer_person_id IS NULL
          AND COALESCE(m.expense_funding, 'owner') = 'owner'
          AND m.archived_at IS NULL
          AND s.archived_at IS NULL
          AND sl.archived_at IS NULL
    ), 0)
    - COALESCE((
        SELECT SUM(sl.owed_amount_cents)
        FROM split_lines sl
        JOIN splits s
            ON s.id = sl.split_id
        WHERE s.payer_person_id = p.id
          AND sl.participant_kind = 'user'
          AND s.archived_at IS NULL
          AND sl.archived_at IS NULL
    ), 0)
    - COALESCE((
        SELECT SUM(m.amount_cents)
        FROM movements m
        WHERE m.type = 'settlement'
          AND m.person_id = p.id
          AND m.settlement_direction = 'person_to_user'
          AND m.archived_at IS NULL
    ), 0)
    + COALESCE((
        SELECT SUM(m.amount_cents)
        FROM movements m
        WHERE m.type = 'settlement'
          AND m.person_id = p.id
          AND m.settlement_direction = 'user_to_person'
          AND m.archived_at IS NULL
    ), 0) AS balance_cents
FROM people p;
