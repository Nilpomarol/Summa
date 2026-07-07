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

-- 4. PEOPLE, TRIPS & TAGS
INSERT INTO people (id, name, created_at, updated_at) VALUES ('p-anna', 'Anna', '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');

INSERT INTO trips (id, name, type, status, start_date, end_date, created_at, updated_at) VALUES
('t-berlin', 'Cap de setmana Berlín', 'trip', 'active', '2026-04-10', '2026-04-13', '2026-01-15T00:00:00Z', '2026-01-15T00:00:00Z'),
('t-pirineus', 'Escapada Pirineus', 'trip', 'finished', '2026-03-20', '2026-03-22', '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z'),
('t-japo', 'Viatge Japó 2026', 'trip', 'planned', '2026-10-15', '2026-10-30', '2026-04-01T00:00:00Z', '2026-04-01T00:00:00Z');

INSERT INTO tags (id, name, trip_id, created_at, updated_at) VALUES
('tag-ber-vols', 'Vols & Transports', 't-berlin', '2026-01-15T00:00:00Z', '2026-01-15T00:00:00Z'),
('tag-ber-hotel', 'Hotel & Allotjament', 't-berlin', '2026-01-15T00:00:00Z', '2026-01-15T00:00:00Z'),
('tag-ber-rest', 'Restaurants & Tastets', 't-berlin', '2026-01-15T00:00:00Z', '2026-01-15T00:00:00Z'),
('tag-ber-museums', 'Museus & Entrades', 't-berlin', '2026-01-15T00:00:00Z', '2026-01-15T00:00:00Z'),
('tag-pir-allotj', 'Hotel Rural', 't-pirineus', '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z'),
('tag-pir-esqui', 'Forfait & Esquí', 't-pirineus', '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z'),
('tag-pir-rest', 'Gastronomia de Muntanya', 't-pirineus', '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z'),
('tag-pir-gas', 'Benzina & Peatges', 't-pirineus', '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z'),
('tag-japo-vols', 'Vols Internacionals', 't-japo', '2026-04-01T00:00:00Z', '2026-04-01T00:00:00Z'),
('tag-japo-jrpass', 'JR Pass & Transport', 't-japo', '2026-04-01T00:00:00Z', '2026-04-01T00:00:00Z'),
('tag-japo-reserves', 'Hotels & Ryokans', 't-japo', '2026-04-01T00:00:00Z', '2026-04-01T00:00:00Z');

-- 5. RECURRING TEMPLATES
INSERT INTO templates (id, type, amount_cents, account_id, category_id, name, frequency, next_due_date, created_at, updated_at) VALUES
('tmpl-rent', 'expense', 95000, 'acc-main', 'cat-rent', 'Lloguer Pis', 'monthly', '2026-04-01', '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z'),
('tmpl-netflix', 'expense', 1799, 'acc-main', 'cat-subs', 'Netflix', 'monthly', '2026-04-05', '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z'),
('tmpl-gym', 'expense', 4500, 'acc-main', 'cat-health', 'Gimnàs', 'monthly', '2026-04-01', '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');

-- 6. MOVEMENTS
INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, is_one_time, created_at, updated_at) VALUES
('m-jan-sal', 'income', 245000, '2026-01-01', 'acc-main', 'cat-salary', NULL, NULL, 'Nòmina Gener', 0, '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
('m-jan-rent', 'expense', 95000, '2026-01-01', 'acc-main', 'cat-rent', NULL, NULL, 'Lloguer Gener', 0, '2026-01-01T10:00:00Z', '2026-01-01T10:00:00Z'),
('m-jan-gro1', 'expense', 6245, '2026-01-03', 'acc-main', 'cat-food', NULL, NULL, 'Mercadona', 0, '2026-01-03T18:00:00Z', '2026-01-03T18:00:00Z'),
('m-feb-sal', 'income', 245000, '2026-02-01', 'acc-main', 'cat-salary', NULL, NULL, 'Nòmina Febrer', 0, '2026-02-01T09:00:00Z', '2026-02-01T09:00:00Z'),
('m-feb-vols', 'expense', 12500, '2026-02-10', 'acc-main', 'cat-transport', 't-berlin', 'tag-ber-vols', 'Vols Berlín (Ryanair)', 0, '2026-02-10T15:00:00Z', '2026-02-10T15:00:00Z'),
('m-feb-hotel-ber', 'expense', 24000, '2026-02-15', 'acc-main', 'cat-rent', 't-berlin', 'tag-ber-hotel', 'Hotel Berlín Mitte (3 nits)', 0, '2026-02-15T18:00:00Z', '2026-02-15T18:00:00Z'),
('m-mar-museum-ber', 'expense', 3500, '2026-03-02', 'acc-main', 'cat-leisure', 't-berlin', 'tag-ber-museums', 'Entrades Torre TV & Illa Museus', 0, '2026-03-02T11:00:00Z', '2026-03-02T11:00:00Z'),
('m-mar-comp', 'expense', 145000, '2026-03-05', 'acc-main', 'cat-leisure', NULL, NULL, 'Nou Monitor 4K', 1, '2026-03-05T16:00:00Z', '2026-03-05T16:00:00Z'),
('m-mar-hotel-pir', 'expense', 18000, '2026-03-18', 'acc-main', 'cat-rent', 't-pirineus', 'tag-pir-allotj', 'Hotel Rural Vall de Núria', 0, '2026-03-18T16:00:00Z', '2026-03-18T16:00:00Z'),
('m-mar-gas-pir', 'expense', 5500, '2026-03-20', 'acc-main', 'cat-transport', 't-pirineus', 'tag-pir-gas', 'Benzina anada Pirineus', 0, '2026-03-20T08:00:00Z', '2026-03-20T08:00:00Z'),
('m-mar-forfait-pir', 'expense', 9200, '2026-03-20', 'acc-main', 'cat-leisure', 't-pirineus', 'tag-pir-esqui', 'Forfait 2 dies La Molina', 0, '2026-03-20T09:30:00Z', '2026-03-20T09:30:00Z'),
('m-mar-lloguer-pir', 'expense', 4500, '2026-03-20', 'acc-main', 'cat-leisure', 't-pirineus', 'tag-pir-esqui', 'Lloguer equips d''esquí', 0, '2026-03-20T10:00:00Z', '2026-03-20T10:00:00Z'),
('m-mar-dinar-pir', 'expense', 4800, '2026-03-21', 'acc-main', 'cat-leisure', 't-pirineus', 'tag-pir-rest', 'Dinar Trinxat & Escudella', 0, '2026-03-21T14:00:00Z', '2026-03-21T14:00:00Z'),
('m-mar-sopar-pir', 'expense', 5600, '2026-03-21', 'acc-main', 'cat-leisure', 't-pirineus', 'tag-pir-rest', 'Sopar Fondue de Formatge', 0, '2026-03-21T21:00:00Z', '2026-03-21T21:00:00Z'),
('m-mar-peatge-pir', 'expense', 1450, '2026-03-22', 'acc-cash', 'cat-transport', 't-pirineus', 'tag-pir-gas', 'Peatge Túnel del Cadí', 0, '2026-03-22T19:00:00Z', '2026-03-22T19:00:00Z'),
('m-apr-pass-ber', 'expense', 2600, '2026-04-09', 'acc-main', 'cat-transport', 't-berlin', 'tag-ber-vols', 'Berlin WelcomeCard 72h', 0, '2026-04-09T17:00:00Z', '2026-04-09T17:00:00Z'),
('m-apr-sopar-ber', 'expense', 6450, '2026-04-10', 'acc-main', 'cat-leisure', 't-berlin', 'tag-ber-rest', 'Sopar Schnitzel & Cervesa', 0, '2026-04-10T21:00:00Z', '2026-04-10T21:00:00Z'),
('m-apr-dinar-ber', 'expense', 3200, '2026-04-11', 'acc-cash', 'cat-leisure', 't-berlin', 'tag-ber-rest', 'Dinar Currywurst & Mercat', 0, '2026-04-11T13:30:00Z', '2026-04-11T13:30:00Z'),
('m-apr-cafe-ber', 'expense', 1450, '2026-04-12', 'acc-cash', 'cat-leisure', 't-berlin', 'tag-ber-rest', 'Cafè i pastisseria Kreuzberg', 0, '2026-04-12T16:00:00Z', '2026-04-12T16:00:00Z'),
('m-apr-souvenir-ber', 'expense', 2200, '2026-04-12', 'acc-cash', 'cat-leisure', 't-berlin', NULL, 'Recordets & Souvenirs Berlín', 0, '2026-04-12T18:00:00Z', '2026-04-12T18:00:00Z'),
('m-may-vols-japo', 'expense', 89000, '2026-05-10', 'acc-main', 'cat-transport', 't-japo', 'tag-japo-vols', 'Vols Barcelona - Tòquio (Emirates)', 0, '2026-05-10T11:00:00Z', '2026-05-10T11:00:00Z'),
('m-jun-jrpass-japo', 'expense', 32000, '2026-06-18', 'acc-main', 'cat-transport', 't-japo', 'tag-japo-jrpass', 'Japan Rail Pass 7 dies', 0, '2026-06-18T10:00:00Z', '2026-06-18T10:00:00Z'),
('m-jun-ryokan-japo', 'expense', 21000, '2026-06-25', 'acc-main', 'cat-rent', 't-japo', 'tag-japo-reserves', 'Paga i senyal Ryokan Kyoto', 0, '2026-06-25T15:00:00Z', '2026-06-25T15:00:00Z');

-- 7. SHARED DINNER
INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES
('m-feb-din-shared', 'expense', 8000, '2026-02-14', 'acc-main', 'cat-leisure', 'Sopar Sant Valentí', '2026-02-14T21:30:00Z', '2026-02-14T21:30:00Z');
INSERT INTO splits (id, movement_id, entry_method, created_at, updated_at) VALUES ('s-feb-din', 'm-feb-din-shared', 'equal', '2026-02-14T21:30:00Z', '2026-02-14T21:30:00Z');
INSERT INTO split_lines (id, split_id, participant_kind, person_id, owed_amount_cents, created_at, updated_at) VALUES
('sl-feb-din-u', 's-feb-din', 'user', NULL, 4000, '2026-02-14T21:30:00Z', '2026-02-14T21:30:00Z'),
('sl-feb-din-a', 's-feb-din', 'person', 'p-anna', 4000, '2026-02-14T21:30:00Z', '2026-02-14T21:30:00Z');

-- 8. LINK RECURRING ITEMS
UPDATE movements SET template_id = 'tmpl-rent' WHERE name LIKE 'Lloguer%';

COMMIT;

