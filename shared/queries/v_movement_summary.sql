CREATE VIEW v_movement_summary AS
SELECT
    movements.id,
    movements.type,
    movements.amount_cents,
    movements.date,
    movements.account_id,
    accounts.name AS account_name,
    accounts.color AS account_color,
    movements.dest_account_id,
    destination_accounts.name AS destination_account_name,
    destination_accounts.color AS destination_account_color,
    movements.category_id,
    categories.name AS category_name,
    categories.nature AS category_nature,
    categories.icon AS category_icon,
    categories.color AS category_color,
    movements.trip_id,
    trips.name AS trip_name,
    trips.color AS trip_color,
    movements.tag_id,
    tags.name AS tag_name,
    movements.template_id,
    movements.name,
    movements.payee,
    movements.notes,
    movements.is_one_time,
    movements.created_at,
    movements.updated_at,
    movements.archived_at,
    movements.refunds_expense_id,
    refunded_expense.name AS refunds_expense_name,
    CASE WHEN refunded_expense.archived_at IS NOT NULL THEN 1 ELSE 0 END AS refunds_expense_archived,
    NULLIF((
        SELECT paid_by_people.name
        FROM splits AS paid_by_split
        JOIN split_lines AS paid_by_user_line
            ON paid_by_user_line.split_id = paid_by_split.id
           AND paid_by_user_line.participant_kind = 'user'
           AND paid_by_user_line.owed_amount_cents = 0
           AND paid_by_user_line.archived_at IS NULL
        JOIN split_lines AS paid_by_person_line
            ON paid_by_person_line.split_id = paid_by_split.id
           AND paid_by_person_line.participant_kind = 'person'
           AND paid_by_person_line.owed_amount_cents = movements.amount_cents
           AND paid_by_person_line.archived_at IS NULL
        JOIN people AS paid_by_people
            ON paid_by_people.id = paid_by_person_line.person_id
        WHERE paid_by_split.movement_id = movements.id
          AND paid_by_split.archived_at IS NULL
          AND (
              SELECT COUNT(*)
              FROM split_lines AS positive_person_line
              WHERE positive_person_line.split_id = paid_by_split.id
                AND positive_person_line.participant_kind = 'person'
                AND positive_person_line.owed_amount_cents > 0
                AND positive_person_line.archived_at IS NULL
          ) = 1
        LIMIT 1
    ), '') AS paid_by_person_name,
    COALESCE(v_movement_shared.is_shared, 0) AS is_shared,
    CASE
        WHEN movements.type = 'expense' THEN COALESCE(movements.expense_funding, 'owner')
        WHEN movements.type IN ('income','transfer','settlement','refund') THEN 'owner'
        ELSE NULL
    END AS financing_kind,
    movements.person_id AS payer_id,
    movements.settlement_direction,
    settlement_people.name AS settlement_person_name,
    CASE
        WHEN v_movement_shared.is_shared = 1 AND movements.type = 'expense'
        THEN COALESCE((
            SELECT sl.owed_amount_cents
            FROM splits s
            JOIN split_lines sl
                ON sl.split_id = s.id
               AND sl.participant_kind = 'user'
               AND sl.archived_at IS NULL
            WHERE s.movement_id = movements.id
              AND s.archived_at IS NULL
            LIMIT 1
        ), -1)
        ELSE -1
    END AS user_share_cents,
    CASE WHEN movements.template_id IS NOT NULL THEN 1 ELSE 0 END AS is_recurring
FROM movements
JOIN accounts
    ON accounts.id = movements.account_id
LEFT JOIN accounts AS destination_accounts
    ON destination_accounts.id = movements.dest_account_id
LEFT JOIN categories
    ON categories.id = movements.category_id
LEFT JOIN trips
    ON trips.id = movements.trip_id
LEFT JOIN tags
    ON tags.id = movements.tag_id
LEFT JOIN people AS settlement_people
    ON settlement_people.id = movements.person_id
LEFT JOIN v_movement_shared
    ON v_movement_shared.movement_id = movements.id
LEFT JOIN movements AS refunded_expense
    ON refunded_expense.id = movements.refunds_expense_id
WHERE movements.archived_at IS NULL

UNION ALL

SELECT
    s.id,
    'external_expense' AS type,
    s.total_amount_cents AS amount_cents,
    IFNULL(s.date, '') AS date,
    NULL AS account_id,
    NULL AS account_name,
    NULL AS account_color,
    NULL AS dest_account_id,
    NULL AS destination_account_name,
    NULL AS destination_account_color,
    s.category_id,
    c.name AS category_name,
    c.nature AS category_nature,
    c.icon AS category_icon,
    c.color AS category_color,
    s.trip_id,
    t.name AS trip_name,
    t.color AS trip_color,
    s.tag_id,
    tg.name AS tag_name,
    NULL AS template_id,
    s.description AS name,
    NULL AS payee,
    NULL AS notes,
    0 AS is_one_time,
    s.created_at,
    s.updated_at,
    s.archived_at,
    NULL AS refunds_expense_id,
    NULL AS refunds_expense_name,
    0 AS refunds_expense_archived,
    p.name AS paid_by_person_name,
    0 AS is_shared,
    'person' AS financing_kind,
    p.id AS payer_id,
    NULL AS settlement_direction,
    NULL AS settlement_person_name,
    sl.owed_amount_cents AS user_share_cents,
    0 AS is_recurring
FROM splits s
JOIN split_lines sl
    ON sl.split_id = s.id
   AND sl.participant_kind = 'user'
   AND sl.archived_at IS NULL
JOIN people p ON p.id = s.payer_person_id
LEFT JOIN categories c ON c.id = s.category_id
LEFT JOIN trips t ON t.id = s.trip_id
LEFT JOIN tags tg ON tg.id = s.tag_id
WHERE s.movement_id IS NULL
  AND s.payer_person_id IS NOT NULL
  AND s.archived_at IS NULL

UNION ALL

SELECT
    contribution.id,
    'contribution' AS type,
    contribution.amount_cents,
    contribution.date,
    contribution.source_account_id AS account_id,
    source_account.name AS account_name,
    source_account.color AS account_color,
    contribution.shared_account_id AS dest_account_id,
    shared_account.name AS destination_account_name,
    shared_account.color AS destination_account_color,
    NULL AS category_id,
    NULL AS category_name,
    NULL AS category_nature,
    NULL AS category_icon,
    NULL AS category_color,
    NULL AS trip_id,
    NULL AS trip_name,
    NULL AS trip_color,
    NULL AS tag_id,
    NULL AS tag_name,
    NULL AS template_id,
    contribution.name,
    NULL AS payee,
    contribution.notes,
    0 AS is_one_time,
    contribution.created_at,
    contribution.updated_at,
    contribution.archived_at,
    NULL AS refunds_expense_id,
    NULL AS refunds_expense_name,
    0 AS refunds_expense_archived,
    contributor.name AS paid_by_person_name,
    0 AS is_shared,
    CASE contribution.contributor_kind WHEN 'user' THEN 'owner' ELSE 'person' END AS financing_kind,
    contribution.person_id AS payer_id,
    NULL AS settlement_direction,
    NULL AS settlement_person_name,
    -1 AS user_share_cents,
    0 AS is_recurring
FROM account_contributions contribution
JOIN accounts shared_account
    ON shared_account.id = contribution.shared_account_id
LEFT JOIN accounts source_account
    ON source_account.id = contribution.source_account_id
LEFT JOIN people contributor
    ON contributor.id = contribution.person_id
WHERE contribution.archived_at IS NULL;
