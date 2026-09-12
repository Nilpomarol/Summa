#!/usr/bin/env python3
"""Build a current-schema Summa database containing synthetic portfolio data only."""

from __future__ import annotations

import argparse
import sqlite3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
VIEW_FILES = (
    "v_movement_shared.sql",
    "v_movement_summary.sql",
    "v_account_flow.sql",
    "v_account_balance.sql",
    "v_account_value.sql",
    "v_actual_expense.sql",
    "v_actual_income.sql",
    "v_person_balance.sql",
    "v_trip_actual_total.sql",
    "v_goal_allocation.sql",
    "v_goal_progress.sql",
    "v_account_allocation.sql",
)


DEMO_SQL = r"""
BEGIN IMMEDIATE;

INSERT INTO meta (key, value) VALUES
    ('schema_version', '19'),
    ('snapshot_version', '0');

INSERT INTO accounts (
    id, name, starting_balance_cents, type, icon, color, is_default, display_order,
    low_balance_threshold_cents, ownership_kind, created_at, updated_at
) VALUES
    ('acc-main', 'Compte del dia a dia', 320000, 'bank', 'account_balance', '#7D5260', 1, 0, 50000, 'personal', '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('acc-savings', 'Estalvis', 850000, 'savings', 'savings', '#3F6654', 0, 1, NULL, 'personal', '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('acc-cash', 'Efectiu', 6500, 'cash', 'payments', '#B7791F', 0, 2, 2000, 'personal', '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('acc-home', 'Llar compartida', 160000, 'bank', 'home', '#526D82', 0, 3, 30000, 'personal', '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z');

INSERT INTO categories (id, name, kind, nature, icon, color, display_order, created_at, updated_at) VALUES
    ('cat-salary', 'Nòmina', 'income', 'fixed', 'payments', '#3F6654', 0, '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('cat-home', 'Habitatge', 'expense', 'fixed', 'home', '#6B5B73', 1, '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('cat-food', 'Alimentació', 'expense', 'variable', 'shopping_cart', '#C06C45', 2, '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('cat-leisure', 'Oci i restaurants', 'expense', 'variable', 'restaurant', '#9A5F72', 3, '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('cat-transport', 'Transport', 'expense', 'variable', 'directions_car', '#557A95', 4, '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('cat-health', 'Salut', 'expense', 'variable', 'medical_services', '#A85252', 5, '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('cat-subscriptions', 'Subscripcions', 'expense', 'fixed', 'subscriptions', '#5E716A', 6, '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('cat-travel', 'Viatges', 'expense', 'variable', 'flight', '#8A6F4D', 7, '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('cat-shopping', 'Compres', 'expense', 'variable', 'shopping_bag', '#8B6A8B', 8, '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z');

INSERT INTO people (id, name, avatar, color, notes, created_at, updated_at) VALUES
    ('person-laia', 'Laia (demo)', NULL, '#8C5E78', 'Persona fictícia per a les captures', '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('person-marc', 'Marc (demo)', NULL, '#4F718C', 'Persona fictícia per a les captures', '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z');

INSERT INTO account_members (
    id, account_id, participant_kind, person_id, ownership_basis_points,
    default_expense_basis_points, created_at, updated_at
) VALUES
    ('member-home-owner', 'acc-home', 'user', NULL, 5000, 5000, '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('member-home-laia', 'acc-home', 'person', 'person-laia', 5000, 5000, '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z');

UPDATE accounts
SET ownership_kind = 'shared', updated_at = '2026-01-01T09:01:00Z'
WHERE id = 'acc-home';

INSERT INTO trips (
    id, name, type, status, start_date, end_date, icon, color, notes,
    default_account_id, created_at, updated_at
) VALUES
    ('trip-lisbon', 'Cap de setmana a Lisboa', 'trip', 'finished', '2026-08-21', '2026-08-24', 'flight', '#A66A4C', 'Viatge completament fictici', 'acc-main', '2026-06-12T10:00:00Z', '2026-08-24T20:00:00Z'),
    ('trip-pyrenees', 'Escapada als Pirineus', 'trip', 'planned', '2026-10-16', '2026-10-18', 'landscape', '#55745B', 'Viatge completament fictici', 'acc-main', '2026-08-20T10:00:00Z', '2026-08-20T10:00:00Z');

INSERT INTO tags (id, name, icon, color, trip_id, created_at, updated_at) VALUES
    ('tag-lisbon-travel', 'Vols i transport', 'flight', '#557A95', 'trip-lisbon', '2026-06-12T10:00:00Z', '2026-06-12T10:00:00Z'),
    ('tag-lisbon-stay', 'Allotjament', 'hotel', '#6B5B73', 'trip-lisbon', '2026-06-12T10:00:00Z', '2026-06-12T10:00:00Z'),
    ('tag-lisbon-food', 'Àpats', 'restaurant', '#C06C45', 'trip-lisbon', '2026-06-12T10:00:00Z', '2026-06-12T10:00:00Z');

INSERT INTO budgets (
    id, scope, category_id, trip_id, period, limit_amount_cents,
    alert_threshold_percent, include_trip_expenses, include_extraordinary_expenses,
    created_at, updated_at
) VALUES
    ('budget-month', 'overall_month', NULL, NULL, 'monthly', 180000, 80, 0, 0, '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('budget-food', 'category', 'cat-food', NULL, 'monthly', 35000, 80, 1, 1, '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('budget-leisure', 'category', 'cat-leisure', NULL, 'monthly', 22000, 80, 1, 1, '2026-01-01T09:00:00Z', '2026-01-01T09:00:00Z'),
    ('budget-lisbon', 'trip', NULL, 'trip-lisbon', 'one_off', 65000, 80, 1, 1, '2026-06-12T10:00:00Z', '2026-06-12T10:00:00Z');

INSERT INTO goals (
    id, name, target_amount_cents, target_date, account_id, funding_mode, status,
    icon, color, display_order, notes, created_at, updated_at
) VALUES
    ('goal-emergency', 'Coixí d’emergència', 500000, '2027-02-28', 'acc-savings', 'allocations', 'active', 'shield', '#3F6654', 0, 'Objectiu fictici', '2026-03-01T09:00:00Z', '2026-09-01T09:00:00Z'),
    ('goal-camera', 'Càmera nova', 120000, '2027-05-31', 'acc-savings', 'allocations', 'active', 'photo_camera', '#8C5E78', 1, 'Objectiu fictici', '2026-06-01T09:00:00Z', '2026-09-01T09:00:00Z');

INSERT INTO goal_allocations (id, goal_id, account_id, date, amount_cents, notes, created_at, updated_at) VALUES
    ('goal-emergency-a1', 'goal-emergency', 'acc-savings', '2026-03-01', 200000, 'Reserva inicial', '2026-03-01T09:00:00Z', '2026-03-01T09:00:00Z'),
    ('goal-emergency-a2', 'goal-emergency', 'acc-savings', '2026-08-01', 50000, 'Aportació d’estiu', '2026-08-01T09:00:00Z', '2026-08-01T09:00:00Z'),
    ('goal-camera-a1', 'goal-camera', 'acc-savings', '2026-06-01', 40000, 'Primera reserva', '2026-06-01T09:00:00Z', '2026-06-01T09:00:00Z');

INSERT INTO templates (
    id, type, amount_cents, account_id, category_id, name, payee, frequency,
    day_of_month, next_due_date, amount_is_variable, status, created_at, updated_at
) VALUES
    ('template-salary', 'income', 265000, 'acc-main', 'cat-salary', 'Nòmina', 'Empresa de demostració', 'monthly', 1, '2026-10-01', 0, 'active', '2026-01-01T09:00:00Z', '2026-09-01T09:00:00Z'),
    ('template-rent', 'expense', 98000, 'acc-main', 'cat-home', 'Lloguer', 'Propietat de demostració', 'monthly', 2, '2026-10-02', 0, 'active', '2026-01-01T09:00:00Z', '2026-09-02T09:00:00Z'),
    ('template-streaming', 'expense', 1499, 'acc-main', 'cat-subscriptions', 'Cinema en línia', 'Servei de demostració', 'monthly', 8, '2026-10-08', 0, 'active', '2026-01-01T09:00:00Z', '2026-09-08T09:00:00Z');

-- Earlier months make trends and comparisons meaningful.
INSERT INTO movements (
    id, type, amount_cents, date, account_id, dest_account_id, name, payee,
    category_id, template_id, created_at, updated_at
) VALUES
    ('mov-jul-salary', 'income', 265000, '2026-07-01', 'acc-main', NULL, 'Nòmina de juliol', 'Empresa de demostració', 'cat-salary', 'template-salary', '2026-07-01T08:00:00Z', '2026-07-01T08:00:00Z'),
    ('mov-jul-rent', 'expense', 98000, '2026-07-02', 'acc-main', NULL, 'Lloguer de juliol', 'Propietat de demostració', 'cat-home', 'template-rent', '2026-07-02T08:00:00Z', '2026-07-02T08:00:00Z'),
    ('mov-jul-food', 'expense', 28640, '2026-07-18', 'acc-main', NULL, 'Compra del mes', 'Mercat del barri', 'cat-food', NULL, '2026-07-18T18:00:00Z', '2026-07-18T18:00:00Z'),
    ('mov-jul-leisure', 'expense', 13400, '2026-07-23', 'acc-main', NULL, 'Sopar amb amistats', 'Restaurant de demostració', 'cat-leisure', NULL, '2026-07-23T21:00:00Z', '2026-07-23T21:00:00Z'),
    ('mov-aug-salary', 'income', 265000, '2026-08-01', 'acc-main', NULL, 'Nòmina d’agost', 'Empresa de demostració', 'cat-salary', 'template-salary', '2026-08-01T08:00:00Z', '2026-08-01T08:00:00Z'),
    ('mov-aug-rent', 'expense', 98000, '2026-08-02', 'acc-main', NULL, 'Lloguer d’agost', 'Propietat de demostració', 'cat-home', 'template-rent', '2026-08-02T08:00:00Z', '2026-08-02T08:00:00Z'),
    ('mov-aug-food', 'expense', 31250, '2026-08-14', 'acc-main', NULL, 'Compra del mes', 'Mercat del barri', 'cat-food', NULL, '2026-08-14T18:00:00Z', '2026-08-14T18:00:00Z'),
    ('mov-aug-shopping', 'expense', 8990, '2026-08-17', 'acc-main', NULL, 'Sabates', 'Botiga de demostració', 'cat-shopping', NULL, '2026-08-17T17:00:00Z', '2026-08-17T17:00:00Z'),
    ('mov-trip-flight', 'expense', 16800, '2026-08-18', 'acc-main', NULL, 'Vols a Lisboa', 'Aerolínia de demostració', 'cat-travel', NULL, '2026-08-18T10:00:00Z', '2026-08-18T10:00:00Z'),
    ('mov-trip-hotel', 'expense', 24600, '2026-08-21', 'acc-main', NULL, 'Hotel a Lisboa', 'Hotel de demostració', 'cat-travel', NULL, '2026-08-21T10:00:00Z', '2026-08-21T10:00:00Z'),
    ('mov-trip-food', 'expense', 7850, '2026-08-22', 'acc-main', NULL, 'Sopar a Lisboa', 'Restaurant de demostració', 'cat-leisure', NULL, '2026-08-22T21:00:00Z', '2026-08-22T21:00:00Z'),
    ('mov-sep-salary', 'income', 265000, '2026-09-01', 'acc-main', NULL, 'Nòmina de setembre', 'Empresa de demostració', 'cat-salary', 'template-salary', '2026-09-01T08:00:00Z', '2026-09-01T08:00:00Z'),
    ('mov-sep-rent', 'expense', 98000, '2026-09-02', 'acc-main', NULL, 'Lloguer de setembre', 'Propietat de demostració', 'cat-home', 'template-rent', '2026-09-02T08:00:00Z', '2026-09-02T08:00:00Z'),
    ('mov-sep-transfer', 'transfer', 40000, '2026-09-03', 'acc-main', 'acc-savings', 'Estalvi mensual', NULL, NULL, NULL, '2026-09-03T08:00:00Z', '2026-09-03T08:00:00Z'),
    ('mov-sep-food-1', 'expense', 7240, '2026-09-04', 'acc-main', NULL, 'Compra setmanal', 'Mercat del barri', 'cat-food', NULL, '2026-09-04T18:00:00Z', '2026-09-04T18:00:00Z'),
    ('mov-sep-transport', 'expense', 2250, '2026-09-05', 'acc-main', NULL, 'Targeta de transport', 'Transport metropolità', 'cat-transport', NULL, '2026-09-05T10:00:00Z', '2026-09-05T10:00:00Z'),
    ('mov-sep-streaming', 'expense', 1499, '2026-09-08', 'acc-main', NULL, 'Cinema en línia', 'Servei de demostració', 'cat-subscriptions', 'template-streaming', '2026-09-08T08:00:00Z', '2026-09-08T08:00:00Z'),
    ('mov-sep-health', 'expense', 1870, '2026-09-09', 'acc-main', NULL, 'Farmàcia', 'Farmàcia de demostració', 'cat-health', NULL, '2026-09-09T17:00:00Z', '2026-09-09T17:00:00Z'),
    ('mov-sep-food-2', 'expense', 4685, '2026-09-10', 'acc-main', NULL, 'Fruita i verdura', 'Mercat del barri', 'cat-food', NULL, '2026-09-10T18:00:00Z', '2026-09-10T18:00:00Z'),
    ('mov-sep-cash', 'expense', 380, '2026-09-11', 'acc-cash', NULL, 'Cafè', 'Cafeteria de demostració', 'cat-leisure', NULL, '2026-09-11T09:00:00Z', '2026-09-11T09:00:00Z');

UPDATE movements
SET trip_id = 'trip-lisbon', tag_id = 'tag-lisbon-travel'
WHERE id = 'mov-trip-flight';
UPDATE movements
SET trip_id = 'trip-lisbon', tag_id = 'tag-lisbon-stay'
WHERE id = 'mov-trip-hotel';
UPDATE movements
SET trip_id = 'trip-lisbon', tag_id = 'tag-lisbon-food'
WHERE id = 'mov-trip-food';
INSERT INTO movements (
    id, type, amount_cents, date, account_id, name, payee, category_id,
    refunds_expense_id, actual_refund_cents, created_at, updated_at
) VALUES (
    'mov-sep-refund', 'refund', 2000, '2026-09-12', 'acc-main',
    'Devolució parcial', 'Botiga de demostració', 'cat-shopping',
    'mov-aug-shopping', 2000, '2026-09-12T10:00:00Z', '2026-09-12T10:00:00Z'
);

-- A shared restaurant bill paid by the owner.
INSERT INTO movements (
    id, type, amount_cents, date, account_id, name, payee, category_id,
    expense_funding, created_at, updated_at
) VALUES
    ('mov-sep-shared-dinner', 'expense', 8600, '2026-09-06', 'acc-main', 'Sopar compartit', 'Restaurant de demostració', 'cat-leisure', 'owner', '2026-09-06T21:00:00Z', '2026-09-06T21:00:00Z');
INSERT INTO splits (id, movement_id, entry_method, created_at, updated_at) VALUES
    ('split-sep-dinner', 'mov-sep-shared-dinner', 'equal', '2026-09-06T21:00:00Z', '2026-09-06T21:00:00Z');
INSERT INTO split_lines (id, split_id, participant_kind, person_id, owed_amount_cents, owed_percent, created_at, updated_at) VALUES
    ('line-sep-dinner-owner', 'split-sep-dinner', 'user', NULL, 4300, 50.0, '2026-09-06T21:00:00Z', '2026-09-06T21:00:00Z'),
    ('line-sep-dinner-laia', 'split-sep-dinner', 'person', 'person-laia', 4300, 50.0, '2026-09-06T21:00:00Z', '2026-09-06T21:00:00Z');

INSERT INTO movements (
    id, type, amount_cents, date, account_id, name, person_id,
    settlement_direction, settlement_scope, created_at, updated_at
) VALUES
    ('mov-sep-settlement', 'settlement', 4300, '2026-09-07', 'acc-main', 'Liquidació del sopar', 'person-laia', 'person_to_user', 'all', '2026-09-07T12:00:00Z', '2026-09-07T12:00:00Z');

-- Shared-home contributions and one expense paid directly by that account.
INSERT INTO account_contributions (
    id, shared_account_id, direction, contributor_kind, person_id, source_account_id,
    amount_cents, date, name, created_at, updated_at
) VALUES
    ('contrib-home-owner', 'acc-home', 'in', 'user', NULL, 'acc-main', 60000, '2026-09-01', 'Aportació mensual', '2026-09-01T09:00:00Z', '2026-09-01T09:00:00Z'),
    ('contrib-home-laia', 'acc-home', 'in', 'person', 'person-laia', NULL, 60000, '2026-09-01', 'Aportació de Laia', '2026-09-01T09:05:00Z', '2026-09-01T09:05:00Z');

INSERT INTO movements (
    id, type, amount_cents, date, account_id, name, payee, category_id,
    expense_funding, shared_split_id, created_at, updated_at
) VALUES
    ('mov-home-groceries', 'expense', 12430, '2026-09-10', 'acc-home', 'Compra per a casa', 'Supermercat de demostració', 'cat-food', 'shared_account', 'split-home-groceries', '2026-09-10T19:00:00Z', '2026-09-10T19:00:00Z');
INSERT INTO splits (id, movement_id, entry_method, created_at, updated_at) VALUES
    ('split-home-groceries', 'mov-home-groceries', 'equal', '2026-09-10T19:00:00Z', '2026-09-10T19:00:00Z');
INSERT INTO split_lines (id, split_id, participant_kind, person_id, owed_amount_cents, owed_percent, created_at, updated_at) VALUES
    ('line-home-owner', 'split-home-groceries', 'user', NULL, 6215, 50.0, '2026-09-10T19:00:00Z', '2026-09-10T19:00:00Z'),
    ('line-home-laia', 'split-home-groceries', 'person', 'person-laia', 6215, 50.0, '2026-09-10T19:00:00Z', '2026-09-10T19:00:00Z');

COMMIT;
"""


def create_database(output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    output.unlink(missing_ok=True)

    connection = sqlite3.connect(output)
    try:
        connection.executescript((ROOT / "shared/schema/schema.sql").read_text(encoding="utf-8"))
        for filename in VIEW_FILES:
            connection.executescript(
                (ROOT / "shared/queries" / filename).read_text(encoding="utf-8")
            )
        connection.executescript(
            (ROOT / "android/app/src/main/assets/shared_account_integrity.sql").read_text(
                encoding="utf-8"
            )
        )
        connection.execute("PRAGMA user_version = 19")
        connection.executescript(DEMO_SQL)

        foreign_key_errors = connection.execute("PRAGMA foreign_key_check").fetchall()
        if foreign_key_errors:
            raise RuntimeError(f"Foreign-key validation failed: {foreign_key_errors}")

        movement_count = connection.execute(
            "SELECT COUNT(*) FROM v_movement_summary"
        ).fetchone()[0]
        if movement_count < 20:
            raise RuntimeError(f"Expected a rich demo ledger, found {movement_count} rows")

        connection.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        connection.execute("PRAGMA journal_mode = DELETE")
    finally:
        connection.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "output",
        nargs="?",
        type=Path,
        default=ROOT / ".preview_tmp" / "summa-portfolio-demo.db",
    )
    args = parser.parse_args()
    create_database(args.output.resolve())
    print(f"Created synthetic portfolio database: {args.output.resolve()}")


if __name__ == "__main__":
    main()
