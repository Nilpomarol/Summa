-- Gestor finances v7 -> v8 migration.
-- Budget rules can choose whether travel and extraordinary spending count toward their limit.

ALTER TABLE budgets
    ADD COLUMN include_trip_expenses INTEGER NOT NULL DEFAULT 1 CHECK (include_trip_expenses IN (0,1));

ALTER TABLE budgets
    ADD COLUMN include_extraordinary_expenses INTEGER NOT NULL DEFAULT 1 CHECK (include_extraordinary_expenses IN (0,1));

UPDATE meta SET value = '8' WHERE key = 'schema_version';
