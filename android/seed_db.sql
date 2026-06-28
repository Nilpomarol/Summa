BEGIN TRANSACTION;

-- 1. CLEANUP
DELETE FROM split_lines;
DELETE FROM splits;
DELETE FROM movements;
DELETE FROM templates;
DELETE FROM tags;
DELETE FROM trips;
DELETE FROM people;
DELETE FROM categories;
DELETE FROM accounts;

-- 2. ACCOUNTS
INSERT INTO accounts (id, name, starting_balance_cents, type, color, is_default, created_at, updated_at) VALUES
('acc-main', 'BBVA Principal', 154020, 'bank', '#004481', 1, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z'),
('acc-savings', 'Compte Estalvi', 450000, 'savings', '#2ECC71', 0, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z'),
('acc-cash', 'Efectiu', 8500, 'cash', '#F1C40F', 0, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');

-- 3. CATEGORIES
INSERT INTO categories (id, name, kind, nature, icon, color, display_order, created_at, updated_at) VALUES
('cat-salary', 'Nòmina', 'income', 'fixed', 'payments', '#2ECC71', 1, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z'),
('cat-rent', 'Habitatge', 'expense', 'fixed', 'home', '#34495E', 2, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z'),
('cat-food', 'Alimentació', 'expense', 'variable', 'shopping_cart', '#E67E22', 3, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z'),
('cat-leisure', 'Oci i Restauració', 'expense', 'variable', 'restaurant', '#9B59B6', 4, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z'),
('cat-transport', 'Transport', 'expense', 'variable', 'directions_car', '#3498DB', 5, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z'),
('cat-health', 'Salut', 'expense', 'variable', 'medical_services', '#E74C3C', 6, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z'),
('cat-subs', 'Subscripcions', 'expense', 'fixed', 'subscriptions', '#1ABC9C', 7, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');

-- 4. PEOPLE & TRIPS
INSERT INTO people (id, name, created_at, updated_at) VALUES ('p-anna', 'Anna', '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');
INSERT INTO trips (id, name, type, status, start_date, created_at, updated_at) VALUES ('t-berlin', 'Cap de setmana Berlín', 'trip', 'planned', '2026-04-10', '2026-01-15T00:00:00Z', '2026-01-15T00:00:00Z');
INSERT INTO tags (id, name, trip_id, created_at, updated_at) VALUES ('tag-vols', 'Vols', 't-berlin', '2026-01-15T00:00:00Z', '2026-01-15T00:00:00Z');

-- 5. RECURRING TEMPLATES
INSERT INTO templates (id, type, amount_cents, account_id, category_id, name, frequency, next_due_date, created_at, updated_at) VALUES
('tmpl-rent', 'expense', 95000, 'acc-main', 'cat-rent', 'Lloguer Pis', 'monthly', '2026-04-01', '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z'),
('tmpl-netflix', 'expense', 1799, 'acc-main', 'cat-subs', 'Netflix', 'monthly', '2026-04-05', '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z'),
('tmpl-gym', 'expense', 4500, 'acc-main', 'cat-health', 'Gimnàs', 'monthly', '2026-04-01', '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');

-- 6. MOVEMENTS
INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES
('m-jan-sal', 'income', 245000, '2026-01-01', 'acc-main', 'cat-salary', 'Nòmina Gener', '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
('m-jan-rent', 'expense', 95000, '2026-01-01', 'acc-main', 'cat-rent', 'Lloguer Gener', '2026-01-01T10:00:00Z', '2026-01-01T10:00:00Z'),
('m-jan-gro1', 'expense', 6245, '2026-01-03', 'acc-main', 'cat-food', 'Mercadona', '2026-01-03T18:00:00Z', '2026-01-03T18:00:00Z'),
('m-feb-sal', 'income', 245000, '2026-02-01', 'acc-main', 'cat-salary', 'Nòmina Febrer', '2026-02-01T09:00:00Z', '2026-02-01T09:00:00Z');

-- 7. SHARED DINNER
INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES
('m-feb-din-shared', 'expense', 8000, '2026-02-14', 'acc-main', 'cat-leisure', 'Sopar Sant Valentí', '2026-02-14T21:30:00Z', '2026-02-14T21:30:00Z');
INSERT INTO splits (id, movement_id, entry_method, created_at, updated_at) VALUES ('s-feb-din', 'm-feb-din-shared', 'equal', '2026-02-14T21:30:00Z', '2026-02-14T21:30:00Z');
INSERT INTO split_lines (id, split_id, participant_kind, person_id, owed_amount_cents, created_at, updated_at) VALUES
('sl-feb-din-u', 's-feb-din', 'user', NULL, 4000, '2026-02-14T21:30:00Z', '2026-02-14T21:30:00Z'),
('sl-feb-din-a', 's-feb-din', 'person', 'p-anna', 4000, '2026-02-14T21:30:00Z', '2026-02-14T21:30:00Z');

-- 8. ONE-TIME PURCHASE
INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, is_one_time, created_at, updated_at) VALUES
('m-mar-comp', 'expense', 145000, '2026-03-05', 'acc-main', 'cat-leisure', 'Nou Monitor 4K', 1, '2026-03-05T16:00:00Z', '2026-03-05T16:00:00Z');

-- 9. LINK RECURRING ITEMS
UPDATE movements SET template_id = 'tmpl-rent' WHERE name LIKE 'Lloguer%';

COMMIT;
