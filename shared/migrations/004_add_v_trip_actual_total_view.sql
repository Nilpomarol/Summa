-- Gestor finances v3 → v4 migration.
-- Adds v_trip_actual_total (added to SharedSchema.sq's sharedViewFiles list alongside the
-- P5R-7 trips/tags work; not present in v1-v3 databases since Schema.create() at that time
-- predated this view). Mirrors the 1.sqm precedent (v_movement_summary) — a view referenced
-- by app queries (Trips.sq's activeTrips/tripById/tripActiveOn) must be created by a migration
-- step for existing databases, not just wired into the fresh-install sharedViewFiles list.
--
-- Also recreates v_actual_expense with its now-current shape (tag_id exposed on every branch,
-- fixing F7 — see docs/16-android-audit-findings.md). v_actual_expense has existed since the very
-- first shared-schema commit (P0A-3, before schema_version even existed) purely via
-- sharedViewFiles, so ANY database that was ever fresh-created (Schema.create()) before this
-- edit — at schema v1, v2, or v3 — has an on-disk copy of the view frozen at whatever
-- shared/queries/v_actual_expense.sql said at install time, i.e. without tag_id. No earlier
-- migration ever touched this view (it predates 002/003 entirely), so nothing would ever
-- recreate it for an upgrader without this step: TripAnalysis.sq's tripActualByTag (which
-- selects e.tag_id from v_actual_expense) would fail with "no such column: e.tag_id" forever.
-- Recreated before v_trip_actual_total below since the latter selects from it.

DROP VIEW IF EXISTS v_actual_expense;
CREATE VIEW v_actual_expense AS
SELECT
    m.id AS source_id,
    m.date,
    m.category_id,
    m.trip_id,
    m.tag_id,
    CASE
        WHEN s.id IS NULL THEN m.amount_cents
        ELSE COALESCE((
            SELECT sl.owed_amount_cents
            FROM split_lines sl
            WHERE sl.split_id = s.id
              AND sl.participant_kind = 'user'
              AND sl.archived_at IS NULL
        ), 0)
    END AS amount_cents,
    m.is_one_time
FROM movements m
LEFT JOIN splits s
    ON s.movement_id = m.id
   AND s.archived_at IS NULL
WHERE m.type = 'expense'
  AND m.archived_at IS NULL

UNION ALL

SELECT
    m.id AS source_id,
    m.date,
    m.category_id,
    m.trip_id,
    (
        SELECT e.tag_id
        FROM movements e
        WHERE e.id = m.refunds_expense_id
    ) AS tag_id,
    -COALESCE(m.actual_refund_cents, m.amount_cents) AS amount_cents,
    COALESCE((
        SELECT e.is_one_time
        FROM movements e
        WHERE e.id = m.refunds_expense_id
    ), 0) AS is_one_time
FROM movements m
WHERE m.type = 'refund'
  AND m.archived_at IS NULL

UNION ALL

SELECT
    s.id AS source_id,
    s.date,
    s.category_id,
    s.trip_id,
    s.tag_id,
    COALESCE((
        SELECT sl.owed_amount_cents
        FROM split_lines sl
        WHERE sl.split_id = s.id
          AND sl.participant_kind = 'user'
          AND sl.archived_at IS NULL
    ), 0) AS amount_cents,
    0 AS is_one_time
FROM splits s
WHERE s.payer_person_id IS NOT NULL
  AND s.movement_id IS NULL
  AND s.archived_at IS NULL;

DROP VIEW IF EXISTS v_trip_actual_total;
CREATE VIEW v_trip_actual_total AS
SELECT
    trip_id,
    SUM(amount_cents) AS total_actual_cents
FROM v_actual_expense
WHERE trip_id IS NOT NULL
GROUP BY trip_id;

UPDATE meta SET value = '4' WHERE key = 'schema_version';
