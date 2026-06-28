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

        // 3. PEOPLE & TRIPS
        execute("INSERT INTO people (id, name, created_at, updated_at) VALUES ('p-anna', 'Anna', '2025-12-01T00:00:00Z', '2025-12-01T00:00:00Z');")
        execute("INSERT INTO trips (id, name, type, status, start_date, created_at, updated_at) VALUES ('t-berlin', 'Cap de setmana Berlín', 'trip', 'planned', '2026-04-10', '2026-01-15T00:00:00Z', '2026-01-15T00:00:00Z');")
        execute("INSERT INTO tags (id, name, trip_id, created_at, updated_at) VALUES ('tag-vols', 'Vols', 't-berlin', '2026-01-15T00:00:00Z', '2026-01-15T00:00:00Z');")

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
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, trip_id, tag_id, name, created_at, updated_at) VALUES ('m-feb-vols', 'expense', 12500, '2026-02-10', 'acc-main', 'cat-transport', 't-berlin', 'tag-vols', 'Vols Berlín (Ryanair)', '2026-02-10T15:00:00Z', '2026-02-10T15:00:00Z');")
        
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
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, is_one_time, created_at, updated_at) VALUES ('m-mar-comp', 'expense', 145000, '2026-03-05', 'acc-main', 'cat-leisure', 'Nou Monitor 4K', 1, '2026-03-05T16:00:00Z', '2026-03-05T16:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-mar-gro1', 'expense', 8215, '2026-03-07', 'acc-main', 'cat-food', 'Bonpreu', '2026-03-07T11:00:00Z', '2026-03-07T11:00:00Z');")
        execute("INSERT INTO movements (id, type, amount_cents, date, account_id, category_id, name, created_at, updated_at) VALUES ('m-mar-pharm', 'expense', 1540, '2026-03-10', 'acc-cash', 'cat-health', 'Farmàcia', '2026-03-10T14:00:00Z', '2026-03-10T14:00:00Z');")

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
