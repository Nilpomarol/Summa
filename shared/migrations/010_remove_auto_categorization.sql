-- Gestor finances v9 -> v10 migration.
-- Auto-categorization was never manageable by users, so remove its dormant rule store.

DROP TABLE IF EXISTS auto_cat_rules;

UPDATE meta SET value = '10' WHERE key = 'schema_version';
