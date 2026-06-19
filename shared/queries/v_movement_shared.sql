CREATE VIEW v_movement_shared AS
SELECT
    m.id AS movement_id,
    EXISTS (
        SELECT 1
        FROM splits s
        WHERE s.movement_id = m.id
          AND s.archived_at IS NULL
    ) AS is_shared
FROM movements m;
