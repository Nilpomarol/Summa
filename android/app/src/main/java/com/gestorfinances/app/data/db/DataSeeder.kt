package com.gestorfinances.app.data.db

import app.cash.sqldelight.db.SqlDriver

class DataSeeder(private val driver: SqlDriver) {

    fun seed() {
        driver.execute(null, "PRAGMA foreign_keys = OFF;", 0)
        
        // Cleanup
        val tables = listOf("split_lines", "splits", "movements", "templates", "tags", "trips", "people", "categories", "accounts")
        tables.forEach { driver.execute(null, "DELETE FROM $it;", 0) }

        // 1. ACCOUNTS
        execute("INSERT INTO accounts (id, name, starting_balance_cents, type, color, is_default, created_at, updated_at) VALUES ('acc-main', 'BBVA Principal', 154020, 'bank', '#004481', 1, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');")
        execute("INSERT INTO accounts (id, name, starting_balance_cents, type, color, is_default, created_at, updated_at) VALUES ('acc-savings', 'Compte Estalvi', 450000, 'savings', '#2ECC71', 0, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');")
        execute("INSERT INTO accounts (id, name, starting_balance_cents, type, color, is_default, created_at, updated_at) VALUES ('acc-cash', 'Efectiu', 8500, 'cash', '#F1C40F', 0, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');")

        // 2. CATEGORIES
        execute("INSERT INTO categories (id, name, kind, nature, icon, color, display_order, created_at, updated_at) VALUES ('cat-salary', 'Nòmina', 'income', 'fixed', 'payments', '#2ECC71', 1, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');")
        execute("INSERT INTO categories (id, name, kind, nature, icon, color, display_order, created_at, updated_at) VALUES ('cat-rent', 'Habitatge', 'expense', 'fixed', 'home', '#34495E', 2, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');")
        execute("INSERT INTO categories (id, name, kind, nature, icon, color, display_order, created_at, updated_at) VALUES ('cat-food', 'Alimentació', 'expense', 'variable', 'shopping_cart', '#E67E22', 3, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');")
        execute("INSERT INTO categories (id, name, kind, nature, icon, color, display_order, created_at, updated_at) VALUES ('cat-leisure', 'Oci i Restauració', 'expense', 'variable', 'restaurant', '#9B59B6', 4, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');")
        execute("INSERT INTO categories (id, name, kind, nature, icon, color, display_order, created_at, updated_at) VALUES ('cat-transport', 'Transport', 'expense', 'variable', 'directions_car', '#3498DB', 5, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');")
        execute("INSERT INTO categories (id, name, kind, nature, icon, color, display_order, created_at, updated_at) VALUES ('cat-health', 'Salut', 'expense', 'variable', 'medical_services', '#E74C3C', 6, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');")
        execute("INSERT INTO categories (id, name, kind, nature, icon, color, display_order, created_at, updated_at) VALUES ('cat-subs', 'Subscripcions', 'expense', 'fixed', 'subscriptions', '#1ABC9C', 7, '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');")

        // 3. PEOPLE, TRIPS & TAGS
        execute("INSERT INTO people (id, name, created_at, updated_at) VALUES ('p-anna', 'Anna', '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');")
        
        // Trips
        execute("INSERT INTO trips (id, name, type, status, start_date, end_date, created_at, updated_at) VALUES ('t-berlin', 'Cap de setmana Berlín', 'trip', 'active', '2026-04-10', '2026-04-13', '2026-01-15T00:00:00Z', '2026-01-15T00:00:00Z');")
        execute("INSERT INTO trips (id, name, type, status, start_date, end_date, created_at, updated_at) VALUES ('t-pirineus', 'Escapada Pirineus', 'trip', 'finished', '2026-03-20', '2026-03-22', '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z');")
        execute("INSERT INTO trips (id, name, type, status, start_date, end_date, created_at, updated_at) VALUES ('t-japo', 'Viatge Japó 2026', 'trip', 'planned', '2026-10-15', '2026-10-30', '2026-04-01T00:00:00Z', '2026-04-01T00:00:00Z');")

        // Tags for Berlin
        execute("INSERT INTO tags (id, name, trip_id, created_at, updated_at) VALUES ('tag-ber-vols', 'Vols & Transports', 't-berlin', '2026-01-15T00:00:00Z', '2026-01-15T00:00:00Z');")
        execute("INSERT INTO tags (id, name, trip_id, created_at, updated_at) VALUES ('tag-ber-hotel', 'Hotel & Allotjament', 't-berlin', '2026-01-15T00:00:00Z', '2026-01-15T00:00:00Z');")
        execute("INSERT INTO tags (id, name, trip_id, created_at, updated_at) VALUES ('tag-ber-rest', 'Restaurants & Tastets', 't-berlin', '2026-01-15T00:00:00Z', '2026-01-15T00:00:00Z');")
        execute("INSERT INTO tags (id, name, trip_id, created_at, updated_at) VALUES ('tag-ber-museums', 'Museus & Entrades', 't-berlin', '2026-01-15T00:00:00Z', '2026-01-15T00:00:00Z');")

        // Tags for Pirineus
        execute("INSERT INTO tags (id, name, trip_id, created_at, updated_at) VALUES ('tag-pir-allotj', 'Hotel Rural', 't-pirineus', '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z');")
        execute("INSERT INTO tags (id, name, trip_id, created_at, updated_at) VALUES ('tag-pir-esqui', 'Forfait & Esquí', 't-pirineus', '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z');")
        execute("INSERT INTO tags (id, name, trip_id, created_at, updated_at) VALUES ('tag-pir-rest', 'Gastronomia de Muntanya', 't-pirineus', '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z');")
        execute("INSERT INTO tags (id, name, trip_id, created_at, updated_at) VALUES ('tag-pir-gas', 'Benzina & Peatges', 't-pirineus', '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z');")

        // Tags for Japó
        execute("INSERT INTO tags (id, name, trip_id, created_at, updated_at) VALUES ('tag-japo-vols', 'Vols Internacionals', 't-japo', '2026-04-01T00:00:00Z', '2026-04-01T00:00:00Z');")
        execute("INSERT INTO tags (id, name, trip_id, created_at, updated_at) VALUES ('tag-japo-jrpass', 'JR Pass & Transport', 't-japo', '2026-04-01T00:00:00Z', '2026-04-01T00:00:00Z');")
        execute("INSERT INTO tags (id, name, trip_id, created_at, updated_at) VALUES ('tag-japo-reserves', 'Hotels & Ryokans', 't-japo', '2026-04-01T00:00:00Z', '2026-04-01T00:00:00Z');")

        // 4. RECURRING TEMPLATES
        execute("INSERT INTO templates (id, type, amount_cents, account_id, category_id, name, frequency, next_due_date, created_at, updated_at) VALUES ('tmpl-rent', 'expense', 95000, 'acc-main', 'cat-rent', 'Lloguer Pis', 'monthly', '2026-04-01', '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');")
        execute("INSERT INTO templates (id, type, amount_cents, account_id, category_id, name, frequency, next_due_date, created_at, updated_at) VALUES ('tmpl-netflix', 'expense', 1799, 'acc-main', 'cat-subs', 'Netflix', 'monthly', '2026-04-05', '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');")
        execute("INSERT INTO templates (id, type, amount_cents, account_id, category_id, name, frequency, next_due_date, created_at, updated_at) VALUES ('tmpl-gym', 'expense', 4500, 'acc-main', 'cat-health', 'Gimnàs', 'monthly', '2026-04-01', '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');")

        // 5. MOVEMENTS: JANUARY 2026
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jan-sal', 'income', 245000, '2026-01-01', 'acc-main', 'cat-salary', 'Nòmina Gener', '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jan-rent', 'expense', 95000, '2026-01-01', 'acc-main', 'cat-rent', 'Lloguer Gener', '2026-01-01T10:00:00Z', '2026-01-01T10:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jan-gym', 'expense', 4500, '2026-01-02', 'acc-main', 'cat-health', 'Gimnàs', '2026-01-02T10:00:00Z', '2026-01-02T10:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jan-gro1', 'expense', 6245, '2026-01-03', 'acc-main', 'cat-food', 'Mercadona', '2026-01-03T18:00:00Z', '2026-01-03T18:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jan-netflix', 'expense', 1799, '2026-01-05', 'acc-main', 'cat-subs', 'Netflix', '2026-01-05T08:00:00Z', '2026-01-05T08:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jan-din1', 'expense', 5600, '2026-01-10', 'acc-main', 'cat-leisure', 'Sopar Japonès', '2026-01-10T22:00:00Z', '2026-01-10T22:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jan-gro2', 'expense', 5820, '2026-01-17', 'acc-main', 'cat-food', 'Bonpreu', '2026-01-17T11:00:00Z', '2026-01-17T11:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, dest_account_id, name, created_at, updated_at) VALUES ('m-jan-sav', 'transfer', 40000, '2026-01-20', 'acc-main', 'acc-savings', 'Estalvi mensual', '2026-01-20T10:00:00Z', '2026-01-20T10:00:00Z');")

        // 6. MOVEMENTS: FEBRUARY 2026
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-feb-sal', 'income', 245000, '2026-02-01', 'acc-main', 'cat-salary', 'Nòmina Febrer', '2026-02-01T09:00:00Z', '2026-02-01T09:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-feb-rent', 'expense', 95000, '2026-02-01', 'acc-main', 'cat-rent', 'Lloguer Febrer', '2026-02-01T10:00:00Z', '2026-02-01T10:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-feb-gym', 'expense', 4500, '2026-02-02', 'acc-main', 'cat-health', 'Gimnàs', '2026-02-02T10:00:00Z', '2026-02-02T10:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-feb-gro1', 'expense', 7130, '2026-02-07', 'acc-main', 'cat-food', 'Mercadona', '2026-02-07T12:00:00Z', '2026-02-07T12:00:00Z');")
        
        // Trip Berlin bookings
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-feb-vols', 'expense', 12500, '2026-02-10', 'acc-main', 'cat-transport', 't-berlin', 'tag-ber-vols', 'Vols Berlín (Ryanair)', '2026-02-10T15:00:00Z', '2026-02-10T15:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-feb-hotel-ber', 'expense', 24000, '2026-02-15', 'acc-main', 'cat-rent', 't-berlin', 'tag-ber-hotel', 'Hotel Berlín Mitte (3 nits)', '2026-02-15T18:00:00Z', '2026-02-15T18:00:00Z');")

        // Shared Dinner
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-feb-din-shared', 'expense', 8000, '2026-02-14', 'acc-main', 'cat-leisure', 'Sopar Sant Valentí', '2026-02-14T21:30:00Z', '2026-02-14T21:30:00Z');")
        execute("INSERT INTO splits (id, movement_id, entry_method, created_at, updated_at) VALUES ('s-feb-din', 'm-feb-din-shared', 'equal', '2026-02-14T21:30:00Z', '2026-02-14T21:30:00Z');")
        execute("INSERT INTO split_lines (id, split_id, participant_kind, person_id, owed_amount_cents, created_at, updated_at) VALUES ('sl-feb-din-u', 's-feb-din', 'user', NULL, 4000, '2026-02-14T21:30:00Z', '2026-02-14T21:30:00Z');")
        execute("INSERT INTO split_lines (id, split_id, participant_kind, person_id, owed_amount_cents, created_at, updated_at) VALUES ('sl-feb-din-a', 's-feb-din', 'person', 'p-anna', 4000, '2026-02-14T21:30:00Z', '2026-02-14T21:30:00Z');")

        // Settlement
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, person_id, settlement_direction, notes, created_at, updated_at) VALUES ('m-feb-settle', 'settlement', 4000, '2026-02-16', 'acc-main', 'p-anna', 'person_to_user', 'Bizum sopar', '2026-02-16T12:00:00Z', '2026-02-16T12:00:00Z');")

        // 7. MOVEMENTS: MARCH 2026
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-mar-sal', 'income', 245000, '2026-03-01', 'acc-main', 'cat-salary', 'Nòmina Març', '2026-03-01T09:00:00Z', '2026-03-01T09:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-mar-rent', 'expense', 95000, '2026-03-01', 'acc-main', 'cat-rent', 'Lloguer Març', '2026-03-01T10:00:00Z', '2026-03-01T10:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-mar-museum-ber', 'expense', 3500, '2026-03-02', 'acc-main', 'cat-leisure', 't-berlin', 'tag-ber-museums', 'Entrades Torre TV & Illa Museus', '2026-03-02T11:00:00Z', '2026-03-02T11:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, is_one_time, created_at, updated_at) VALUES ('m-mar-comp', 'expense', 145000, '2026-03-05', 'acc-main', 'cat-leisure', 'Nou Monitor 4K', 1, '2026-03-05T16:00:00Z', '2026-03-05T16:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-mar-gro1', 'expense', 8215, '2026-03-07', 'acc-main', 'cat-food', 'Bonpreu', '2026-03-07T11:00:00Z', '2026-03-07T11:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-mar-pharm', 'expense', 1540, '2026-03-10', 'acc-cash', 'cat-health', 'Farmàcia', '2026-03-10T14:00:00Z', '2026-03-10T14:00:00Z');")
        
        // Trip Pirineus movements
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-mar-hotel-pir', 'expense', 18000, '2026-03-18', 'acc-main', 'cat-rent', 't-pirineus', 'tag-pir-allotj', 'Hotel Rural Vall de Núria', '2026-03-18T16:00:00Z', '2026-03-18T16:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-mar-gas-pir', 'expense', 5500, '2026-03-20', 'acc-main', 'cat-transport', 't-pirineus', 'tag-pir-gas', 'Benzina anada Pirineus', '2026-03-20T08:00:00Z', '2026-03-20T08:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-mar-forfait-pir', 'expense', 9200, '2026-03-20', 'acc-main', 'cat-leisure', 't-pirineus', 'tag-pir-esqui', 'Forfait 2 dies La Molina', '2026-03-20T09:30:00Z', '2026-03-20T09:30:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-mar-lloguer-pir', 'expense', 4500, '2026-03-20', 'acc-main', 'cat-leisure', 't-pirineus', 'tag-pir-esqui', 'Lloguer equips d''esquí', '2026-03-20T10:00:00Z', '2026-03-20T10:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-mar-dinar-pir', 'expense', 4800, '2026-03-21', 'acc-main', 'cat-leisure', 't-pirineus', 'tag-pir-rest', 'Dinar Trinxat & Escudella', '2026-03-21T14:00:00Z', '2026-03-21T14:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-mar-sopar-pir', 'expense', 5600, '2026-03-21', 'acc-main', 'cat-leisure', 't-pirineus', 'tag-pir-rest', 'Sopar Fondue de Formatge', '2026-03-21T21:00:00Z', '2026-03-21T21:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-mar-peatge-pir', 'expense', 1450, '2026-03-22', 'acc-cash', 'cat-transport', 't-pirineus', 'tag-pir-gas', 'Peatge Túnel del Cadí', '2026-03-22T19:00:00Z', '2026-03-22T19:00:00Z');")

        // 7.1 MOVEMENTS: APRIL 2026
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-apr-sal', 'income', 245000, '2026-04-01', 'acc-main', 'cat-salary', 'Nòmina Abril', '2026-04-01T09:00:00Z', '2026-04-01T09:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-apr-rent', 'expense', 95000, '2026-04-01', 'acc-main', 'cat-rent', 'Lloguer Abril', '2026-04-01T10:00:00Z', '2026-04-01T10:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-apr-gym', 'expense', 4500, '2026-04-02', 'acc-main', 'cat-health', 'Gimnàs', '2026-04-02T10:00:00Z', '2026-04-02T10:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-apr-netflix', 'expense', 1799, '2026-04-05', 'acc-main', 'cat-subs', 'Netflix', '2026-04-05T08:00:00Z', '2026-04-05T08:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-apr-gro1', 'expense', 7420, '2026-04-06', 'acc-main', 'cat-food', 'Mercadona', '2026-04-06T18:00:00Z', '2026-04-06T18:00:00Z');")
        
        // Trip Berlin on-site expenses
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-apr-pass-ber', 'expense', 2600, '2026-04-09', 'acc-main', 'cat-transport', 't-berlin', 'tag-ber-vols', 'Berlin WelcomeCard 72h', '2026-04-09T17:00:00Z', '2026-04-09T17:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-apr-sopar-ber', 'expense', 6450, '2026-04-10', 'acc-main', 'cat-leisure', 't-berlin', 'tag-ber-rest', 'Sopar Schnitzel & Cervesa', '2026-04-10T21:00:00Z', '2026-04-10T21:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-apr-dinar-ber', 'expense', 3200, '2026-04-11', 'acc-cash', 'cat-leisure', 't-berlin', 'tag-ber-rest', 'Dinar Currywurst & Mercat', '2026-04-11T13:30:00Z', '2026-04-11T13:30:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-apr-cafe-ber', 'expense', 1450, '2026-04-12', 'acc-cash', 'cat-leisure', 't-berlin', 'tag-ber-rest', 'Cafè i pastisseria Kreuzberg', '2026-04-12T16:00:00Z', '2026-04-12T16:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, name, created_at, updated_at) VALUES ('m-apr-souvenir-ber', 'expense', 2200, '2026-04-12', 'acc-cash', 'cat-leisure', 't-berlin', 'Recordets & Souvenirs Berlín', '2026-04-12T18:00:00Z', '2026-04-12T18:00:00Z');")

        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-apr-din1', 'expense', 4500, '2026-04-18', 'acc-main', 'cat-leisure', 'Restaurant Llesca', '2026-04-18T21:00:00Z', '2026-04-18T21:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, dest_account_id, name, created_at, updated_at) VALUES ('m-apr-sav', 'transfer', 40000, '2026-04-20', 'acc-main', 'acc-savings', 'Estalvi mensual', '2026-04-20T10:00:00Z', '2026-04-20T10:00:00Z');")

        // 7.2 MOVEMENTS: MAY 2026
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-may-sal', 'income', 245000, '2026-05-01', 'acc-main', 'cat-salary', 'Nòmina Maig', '2026-05-01T09:00:00Z', '2026-05-01T09:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-may-rent', 'expense', 95000, '2026-05-01', 'acc-main', 'cat-rent', 'Lloguer Maig', '2026-05-01T10:00:00Z', '2026-05-01T10:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-may-gym', 'expense', 4500, '2026-05-02', 'acc-main', 'cat-health', 'Gimnàs', '2026-05-02T10:00:00Z', '2026-05-02T10:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-may-netflix', 'expense', 1799, '2026-05-05', 'acc-main', 'cat-subs', 'Netflix', '2026-05-05T08:00:00Z', '2026-05-05T08:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-may-gro1', 'expense', 6890, '2026-05-08', 'acc-main', 'cat-food', 'Bonpreu', '2026-05-08T11:00:00Z', '2026-05-08T11:00:00Z');")
        
        // Trip Japó advance flight booking
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-may-vols-japo', 'expense', 89000, '2026-05-10', 'acc-main', 'cat-transport', 't-japo', 'tag-japo-vols', 'Vols Barcelona - Tòquio (Emirates)', '2026-05-10T11:00:00Z', '2026-05-10T11:00:00Z');")

        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, is_one_time, created_at, updated_at) VALUES ('m-may-concert', 'expense', 8500, '2026-05-15', 'acc-main', 'cat-leisure', 'Entrades Concert', 1, '2026-05-15T18:00:00Z', '2026-05-15T18:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, dest_account_id, name, created_at, updated_at) VALUES ('m-may-sav', 'transfer', 40000, '2026-05-20', 'acc-main', 'acc-savings', 'Estalvi mensual', '2026-05-20T10:00:00Z', '2026-05-20T10:00:00Z');")

        // 7.3 MOVEMENTS: JUNE 2026
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jun-sal', 'income', 245000, '2026-06-01', 'acc-main', 'cat-salary', 'Nòmina Juny', '2026-06-01T09:00:00Z', '2026-06-01T09:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jun-rent', 'expense', 95000, '2026-06-01', 'acc-main', 'cat-rent', 'Lloguer Juny', '2026-06-01T10:00:00Z', '2026-06-01T10:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jun-gym', 'expense', 4500, '2026-06-02', 'acc-main', 'cat-health', 'Gimnàs', '2026-06-02T10:00:00Z', '2026-06-02T10:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jun-gro1', 'expense', 8240, '2026-06-04', 'acc-main', 'cat-food', 'Mercadona', '2026-06-04T18:00:00Z', '2026-06-04T18:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jun-netflix', 'expense', 1799, '2026-06-05', 'acc-main', 'cat-subs', 'Netflix', '2026-06-05T08:00:00Z', '2026-06-05T08:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jun-din1', 'expense', 6500, '2026-06-10', 'acc-main', 'cat-leisure', 'Sopar Tapes', '2026-06-10T21:30:00Z', '2026-06-10T21:30:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jun-ticket', 'expense', 3500, '2026-06-12', 'acc-main', 'cat-transport', 'Bitllet de tren', '2026-06-12T14:00:00Z', '2026-06-12T14:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jun-pharm', 'expense', 2150, '2026-06-15', 'acc-cash', 'cat-health', 'Farmàcia', '2026-06-15T10:00:00Z', '2026-06-15T10:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, is_one_time, created_at, updated_at) VALUES ('m-jun-bonus', 'income', 50000, '2026-06-15', 'acc-main', 'cat-salary', 'Extra de Juny', 0, '2026-06-15T12:00:00Z', '2026-06-15T12:00:00Z');")
        
        // Trip Japó train & ryokan bookings
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-jun-jrpass-japo', 'expense', 32000, '2026-06-18', 'acc-main', 'cat-transport', 't-japo', 'tag-japo-jrpass', 'Japan Rail Pass 7 dies', '2026-06-18T10:00:00Z', '2026-06-18T10:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-jun-ryokan-japo', 'expense', 21000, '2026-06-25', 'acc-main', 'cat-rent', 't-japo', 'tag-japo-reserves', 'Paga i senyal Ryokan Kyoto', '2026-06-25T15:00:00Z', '2026-06-25T15:00:00Z');")

        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jun-din2', 'expense', 4800, '2026-06-20', 'acc-main', 'cat-leisure', 'Restaurant Kebab', '2026-06-20T22:00:00Z', '2026-06-20T22:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, dest_account_id, name, created_at, updated_at) VALUES ('m-jun-sav', 'transfer', 50000, '2026-06-22', 'acc-main', 'acc-savings', 'Estalvi extra', '2026-06-22T10:00:00Z', '2026-06-22T10:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jun-gas', 'expense', 4500, '2026-06-25', 'acc-main', 'cat-transport', 'Benzinera', '2026-06-25T11:00:00Z', '2026-06-25T11:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-jun-gro2', 'expense', 5410, '2026-06-27', 'acc-main', 'cat-food', 'Bonpreu', '2026-06-27T17:00:00Z', '2026-06-27T17:00:00Z');")

        // 8. LINK RECURRING ITEMS
        execute("UPDATE movements SET template_id = 'tmpl-rent' WHERE name LIKE 'Lloguer%';")
        execute("UPDATE movements SET template_id = 'tmpl-netflix' WHERE name = 'Netflix';")
        execute("UPDATE movements SET template_id = 'tmpl-gym' WHERE name = 'Gimnàs';")

        driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
    }

    private fun execute(sql: String) {
        driver.execute(null, sql, 0)
    }
}
