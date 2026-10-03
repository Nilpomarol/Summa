#!/usr/bin/env python3
"""Build a current-schema Summa database containing synthetic portfolio data only.

Everything in it is invented: a year of a made-up person's ledger ending on TODAY, enough for
every page to have something to show. The same seed always gives the same database.
"""

from __future__ import annotations

import argparse
import calendar
import random
import sqlite3
from datetime import date
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

# The day the screenshots are taken as: the ledger runs up to it.
TODAY = date(2026, 10, 1)
SCHEMA_VERSION = 23
START = "2025-10-01T09:00:00Z"


class Demo:
    def __init__(self, connection: sqlite3.Connection) -> None:
        self.db = connection
        self.random = random.Random(7)
        self.count = 0

    def insert(self, table: str, **values: object) -> None:
        columns = ", ".join(values)
        marks = ", ".join("?" for _ in values)
        self.db.execute(f"INSERT INTO {table} ({columns}) VALUES ({marks})", tuple(values.values()))

    def stamp(self, day: date, hour: int = 12) -> str:
        return f"{day.isoformat()}T{hour:02d}:00:00Z"

    def next_id(self, prefix: str) -> str:
        self.count += 1
        return f"{prefix}-{self.count:04d}"

    def movement(self, kind: str, cents: int, day: date, account: str | None, name: str, **extra: object) -> str:
        movement_id = self.next_id("mov")
        at = self.stamp(day)
        self.insert(
            "movements", id=movement_id, type=kind, amount_cents=cents, date=day.isoformat(),
            account_id=account, name=name, created_at=at, updated_at=at, **extra,
        )
        return movement_id

    def split(self, movement_id: str, day: date, shares: dict[str | None, int]) -> str:
        """A split of the movement: person id (None for the owner) to cents owed."""
        split_id = self.next_id("split")
        at = self.stamp(day)
        self.insert("splits", id=split_id, movement_id=movement_id, entry_method="exact", created_at=at, updated_at=at)
        for person, cents in shares.items():
            self.insert(
                "split_lines", id=self.next_id("line"), split_id=split_id,
                participant_kind="user" if person is None else "person", person_id=person,
                owed_amount_cents=cents, created_at=at, updated_at=at,
            )
        return split_id

    def expense(self, cents: int, day: date, name: str, category: str, account: str = "acc-main", **extra: object) -> str:
        return self.movement("expense", cents, day, account, name, category_id=category, **extra)

    def shared_expense(self, cents: int, day: date, name: str, category: str, shares: dict[str | None, int], **extra: object) -> str:
        """An expense the owner paid and split with others, who then owe their parts."""
        movement_id = self.expense(cents, day, name, category, expense_funding="owner", **extra)
        self.split(movement_id, day, shares)
        return movement_id

    def home_expense(self, cents: int, day: date, name: str, category: str) -> None:
        """An expense paid by the shared home account, half each."""
        movement_id = self.next_id("mov")
        split_id = self.next_id("split")
        at = self.stamp(day)
        half = cents // 2
        self.insert(
            "movements", id=movement_id, type="expense", amount_cents=cents, date=day.isoformat(),
            account_id="acc-home", name=name, category_id=category, expense_funding="shared_account",
            shared_split_id=split_id, created_at=at, updated_at=at,
        )
        self.insert("splits", id=split_id, movement_id=movement_id, entry_method="exact", created_at=at, updated_at=at)
        for person, owed in ((None, cents - half), ("person-laia", half)):
            self.insert(
                "split_lines", id=self.next_id("line"), split_id=split_id,
                participant_kind="user" if person is None else "person", person_id=person,
                owed_amount_cents=owed, created_at=at, updated_at=at,
            )

    def amount(self, low: float, high: float) -> int:
        return int(round(self.random.uniform(low, high), 2) * 100)


def months_before(today: date, count: int) -> list[tuple[int, int]]:
    year, month = today.year, today.month
    months = []
    for _ in range(count):
        month -= 1
        if month == 0:
            year, month = year - 1, 12
        months.append((year, month))
    return list(reversed(months))


def build(connection: sqlite3.Connection) -> None:
    demo = Demo(connection)
    insert = demo.insert

    insert("meta", key="schema_version", value=str(SCHEMA_VERSION))
    insert("meta", key="snapshot_version", value="0")

    # ---------------------------------------------------------------- accounts, people
    accounts = [
        ("acc-main", "Principal", 240_000, "bank", "account_balance", "#2F6B4F", 1, 30_000),
        ("acc-savings", "Estalvis", 620_000, "savings", "savings", "#4F86A8", 0, None),
        ("acc-cash", "Efectiu", 8_000, "cash", "payments", "#B38535", 0, None),
        ("acc-home", "Compte de casa", 30_000, "bank", "receipt_long", "#7A4F7A", 0, 10_000),
    ]
    for order, (account_id, name, start, kind, icon, color, default, threshold) in enumerate(accounts):
        insert(
            "accounts", id=account_id, name=name, starting_balance_cents=start, type=kind, icon=icon, color=color,
            is_default=default, display_order=order, low_balance_threshold_cents=threshold,
            ownership_kind="personal", created_at=START, updated_at=START,
        )
    for person_id, name, color in (("person-laia", "Laia", "#B0647C"), ("person-marc", "Marc", "#4E6FA3"), ("person-julia", "Júlia", "#B38535")):
        insert("people", id=person_id, name=name, color=color, created_at=START, updated_at=START)
    for member_id, kind, person in (("member-home-owner", "user", None), ("member-home-laia", "person", "person-laia")):
        insert(
            "account_members", id=member_id, account_id="acc-home", participant_kind=kind, person_id=person,
            ownership_basis_points=5000, default_expense_basis_points=5000, created_at=START, updated_at=START,
        )
    connection.execute("UPDATE accounts SET ownership_kind = 'shared' WHERE id = 'acc-home'")

    # ---------------------------------------------------------------- categories
    categories = [
        ("cat-salary", "Nòmina", "income", "fixed", None, "work", "#2F6B4F"),
        ("cat-other-income", "Altres ingressos", "income", "variable", None, "euro", "#66854B"),
        ("cat-home", "Habitatge", "expense", "fixed", None, "home", "#7A5A48"),
        ("cat-bills", "Subministraments", "expense", "fixed", None, "bolt", "#A67C2E"),
        ("cat-groceries", "Supermercat", "expense", "variable", None, "shopping_cart", "#C98553"),
        ("cat-eatout", "Menjar a fora", "expense", "variable", None, "restaurant", "#B5614A"),
        ("cat-restaurant", "Restaurants", "expense", "variable", "cat-eatout", "restaurant", "#A5483F"),
        ("cat-coffee", "Cafès", "expense", "variable", "cat-eatout", "local_cafe", "#8C7B6D"),
        ("cat-transport", "Transport", "expense", "variable", None, "train", "#4F86A8"),
        ("cat-public", "Transport públic", "expense", "variable", "cat-transport", "directions_bus", "#2F6F85"),
        ("cat-fuel", "Benzina", "expense", "variable", "cat-transport", "local_gas_station", "#34496E"),
        ("cat-subs", "Subscripcions", "expense", "fixed", None, "credit_card", "#5B5A9E"),
        ("cat-leisure", "Oci", "expense", "variable", None, "movie", "#8574B3"),
        ("cat-health", "Salut", "expense", "variable", None, "local_pharmacy", "#B0647C"),
        ("cat-shopping", "Compres", "expense", "variable", None, "shopping_bag", "#958630"),
        ("cat-travel", "Viatges", "expense", "variable", None, "flight", "#3E8588"),
    ]
    for order, (category_id, name, kind, nature, parent, icon, color) in enumerate(categories):
        insert(
            "categories", id=category_id, name=name, kind=kind, nature=nature, parent_id=parent, icon=icon,
            color=color, display_order=order, created_at=START, updated_at=START,
        )

    # ---------------------------------------------------------------- recurring items
    templates = [
        ("tpl-salary", "income", 214_000, "cat-salary", "Nòmina", "monthly", 28, "2026-10-28"),
        ("tpl-rent", "expense", 85_000, "cat-home", "Lloguer", "monthly", 1, "2026-11-01"),
        ("tpl-gym", "expense", 3_490, "cat-leisure", "Gimnàs", "monthly", 3, "2026-10-03"),
        ("tpl-internet", "expense", 3_890, "cat-bills", "Fibra i mòbil", "monthly", 5, "2026-10-05"),
        ("tpl-music", "expense", 1_099, "cat-subs", "Música en línia", "monthly", 12, "2026-10-12"),
        ("tpl-series", "expense", 1_399, "cat-subs", "Sèries en línia", "monthly", 20, "2026-10-20"),
        ("tpl-insurance", "expense", 31_200, "cat-home", "Assegurança de la llar", "yearly", 15, "2027-03-15"),
    ]
    for template_id, kind, cents, category, name, frequency, day, next_due in templates:
        insert(
            "templates", id=template_id, type=kind, amount_cents=cents, account_id="acc-main", category_id=category,
            name=name, frequency=frequency, day_of_month=day, next_due_date=next_due, amount_is_variable=0,
            status="active", created_at=START, updated_at=START,
        )

    # ---------------------------------------------------------------- trips
    trips = [
        ("trip-lisbon", "Lisboa", "finished", "2026-08-21", "2026-08-24", "flight", "#3E8588"),
        ("trip-pyrenees", "Escapada als Pirineus", "planned", "2026-10-16", "2026-10-18", "hiking", "#66854B"),
    ]
    for trip_id, name, status, start, end, icon, color in trips:
        insert(
            "trips", id=trip_id, name=name, type="trip", status=status, start_date=start, end_date=end, icon=icon,
            color=color, default_account_id="acc-main", created_at="2026-06-12T10:00:00Z", updated_at="2026-06-12T10:00:00Z",
        )
    for tag_id, name, icon, color in (
        ("tag-transport", "Transport", "flight", "#4F86A8"),
        ("tag-stay", "Allotjament", "hotel", "#7A4F7A"),
        ("tag-food", "Àpats", "restaurant", "#B5614A"),
        ("tag-visits", "Visites", "theater_comedy", "#B38535"),
    ):
        insert("tags", id=tag_id, name=name, icon=icon, color=color, trip_type="trip", created_at=START, updated_at=START)

    # ---------------------------------------------------------------- the monthly budget, limits, goals
    budgets = [
        ("budget-month", "overall_month", None, None, "monthly", 170_000, 0),
        ("budget-home", "category", "cat-home", None, "monthly", 87_000, 0),
        ("budget-groceries", "category", "cat-groceries", None, "monthly", 30_000, 0),
        ("budget-eatout", "category", "cat-eatout", None, "monthly", 16_000, 0),
        ("budget-transport", "category", "cat-transport", None, "monthly", 9_000, 0),
        ("budget-subs", "category", "cat-subs", None, "monthly", 3_000, 0),
        ("budget-shopping-year", "category", "cat-shopping", None, "yearly", 90_000, 1),
        ("budget-lisbon", "trip", None, "trip-lisbon", "one_off", 65_000, 1),
        ("budget-pyrenees", "trip", None, "trip-pyrenees", "one_off", 40_000, 1),
    ]
    for budget_id, scope, category, trip, period, limit, trips_in in budgets:
        insert(
            "budgets", id=budget_id, scope=scope, category_id=category, trip_id=trip, period=period,
            limit_amount_cents=limit, include_trip_expenses=trips_in, include_extraordinary_expenses=1,
            created_at=START, updated_at=START,
        )
        if period == "monthly":
            insert(
                "budget_versions", budget_id=budget_id, from_month="0001-01", category_id=category,
                limit_amount_cents=limit, include_trip_expenses=trips_in, include_extraordinary_expenses=1,
            )
    goals = [
        ("goal-emergency", "Coixí d’emergència", 500_000, "2027-06-30", "savings", "#2F6B4F", [("2026-01-05", 150_000), ("2026-05-04", 100_000), ("2026-09-02", 70_000)]),
        ("goal-camera", "Càmera nova", 120_000, "2027-03-31", "camera_alt", "#8574B3", [("2026-04-06", 30_000), ("2026-08-03", 25_000)]),
        ("goal-japan", "Viatge al Japó", 300_000, "2027-10-31", "flight", "#B5614A", [("2026-07-01", 60_000)]),
    ]
    for order, (goal_id, name, target, target_date, icon, color, allocations) in enumerate(goals):
        insert(
            "goals", id=goal_id, name=name, target_amount_cents=target, target_date=target_date,
            account_id="acc-savings", funding_mode="allocations", status="active", icon=icon, color=color,
            display_order=order, created_at=START, updated_at=START,
        )
        for index, (day, cents) in enumerate(allocations):
            insert(
                "goal_allocations", id=f"{goal_id}-a{index}", goal_id=goal_id, account_id="acc-savings", date=day,
                amount_cents=cents, created_at=f"{day}T09:00:00Z", updated_at=f"{day}T09:00:00Z",
            )

    # ---------------------------------------------------------------- a year of ordinary months
    groceries = ["Compra setmanal", "Mercat", "Fruita i verdura", "Forn i peixateria", "Compra del mes"]
    restaurants = ["Sopar amb amics", "Menú del migdia", "Vermut", "Pizzeria", "Sushi"]
    leisure = ["Cinema", "Concert", "Llibreria", "Exposició", "Escape room"]
    shopping = ["Roba", "Sabates", "Auriculars", "Regal d’aniversari", "Cosa de casa"]
    for year, month in months_before(TODAY, 12):
        last = calendar.monthrange(year, month)[1]
        day = lambda number: date(year, month, min(number, last))
        demo.movement("income", 214_000, day(28), "acc-main", "Nòmina", category_id="cat-salary", template_id="tpl-salary")
        demo.expense(85_000, day(1), "Lloguer", "cat-home", template_id="tpl-rent")
        demo.expense(3_490, day(3), "Gimnàs", "cat-leisure", template_id="tpl-gym")
        demo.expense(3_890, day(5), "Fibra i mòbil", "cat-bills", template_id="tpl-internet")
        demo.expense(1_099, day(12), "Música en línia", "cat-subs", template_id="tpl-music")
        demo.expense(1_399, day(20), "Sèries en línia", "cat-subs", template_id="tpl-series")
        demo.movement("transfer", 25_000, day(2), "acc-main", "Estalvi mensual", dest_account_id="acc-savings")
        demo.expense(2_200, day(4), "Abonament de transport", "cat-public")
        if month % 2 == 0:
            demo.expense(demo.amount(38, 58), day(17), "Benzina", "cat-fuel")
        for number in sorted(demo.random.sample(range(2, 28), 5)):
            demo.expense(demo.amount(16, 62), day(number), demo.random.choice(groceries), "cat-groceries")
        for number in sorted(demo.random.sample(range(2, 28), 3)):
            demo.expense(demo.amount(14, 42), day(number), demo.random.choice(restaurants), "cat-restaurant")
        for number in sorted(demo.random.sample(range(1, 28), 4)):
            demo.expense(demo.amount(1.6, 4.2), day(number), "Cafè", "cat-coffee", account="acc-cash" if number % 3 == 0 else "acc-main")
        for number in sorted(demo.random.sample(range(3, 27), 2)):
            demo.expense(demo.amount(8, 28), day(number), demo.random.choice(leisure), "cat-leisure")
        if month % 3 != 0:
            demo.expense(demo.amount(22, 95), day(demo.random.randint(6, 25)), demo.random.choice(shopping), "cat-shopping")
        if month % 4 == 1:
            demo.expense(demo.amount(9, 24), day(demo.random.randint(6, 25)), "Farmàcia", "cat-health")
        if month in (12, 6):
            demo.movement("income", 12_000, day(22), "acc-main", "Venda de segona mà", category_id="cat-other-income")
        # The home account: both put in the same, and it pays the house's shared costs.
        at = demo.stamp(day(1), 9)
        insert(
            "account_contributions", id=demo.next_id("contrib"), shared_account_id="acc-home", direction="in",
            contributor_kind="user", source_account_id="acc-main", amount_cents=20_000, date=day(1).isoformat(),
            name="Aportació mensual", created_at=at, updated_at=at,
        )
        insert(
            "account_contributions", id=demo.next_id("contrib"), shared_account_id="acc-home", direction="in",
            contributor_kind="person", person_id="person-laia", amount_cents=20_000, date=day(1).isoformat(),
            name="Aportació de la Laia", created_at=at, updated_at=at,
        )
        demo.home_expense(demo.amount(62, 108), day(15), "Llum i gas", "cat-bills")
        demo.home_expense(demo.amount(48, 86), day(9), "Compra de casa", "cat-groceries")
        demo.home_expense(demo.amount(40, 78), day(23), "Compra de casa", "cat-groceries")

    # ---------------------------------------------------------------- shared with people
    dinner = demo.shared_expense(9_600, date(2026, 6, 13), "Sopar d’aniversari", "cat-restaurant", {None: 3_200, "person-laia": 3_200, "person-marc": 3_200})
    demo.movement("settlement", 3_200, date(2026, 6, 15), "acc-main", "La Laia paga el sopar", person_id="person-laia", settlement_direction="person_to_user", settlement_scope="all")
    demo.movement("settlement", 3_200, date(2026, 6, 20), "acc-main", "En Marc paga el sopar", person_id="person-marc", settlement_direction="person_to_user", settlement_scope="all")
    demo.shared_expense(7_500, date(2026, 9, 26), "Sopar al japonès", "cat-restaurant", {None: 3_750, "person-marc": 3_750})
    demo.shared_expense(2_800, date(2026, 9, 19), "Entrades del cinema", "cat-leisure", {None: 1_400, "person-laia": 1_400})
    # Júlia paid for both: the owner owes her half.
    concert = demo.movement("expense", 3_600, date(2026, 9, 12), None, "Entrades del concert", category_id="cat-leisure", payer_person_id="person-julia")
    demo.split(concert, date(2026, 9, 12), {None: 1_800, "person-julia": 1_800})
    refunded = demo.expense(5_990, date(2026, 9, 8), "Jaqueta", "cat-shopping")
    demo.movement("refund", 5_990, date(2026, 9, 14), "acc-main", "Devolució de la jaqueta", category_id="cat-shopping", refunds_expense_id=refunded, actual_refund_cents=5_990)
    del dinner

    # ---------------------------------------------------------------- trips
    lisbon = [
        (date(2026, 8, 3), 16_800, "Vols", "cat-travel", "tag-transport"),
        (date(2026, 8, 21), 3_200, "Taxi de l’aeroport", "cat-travel", "tag-transport"),
        (date(2026, 8, 21), 22_500, "Hotel", "cat-travel", "tag-stay"),
        (date(2026, 8, 21), 4_350, "Sopar a l’Alfama", "cat-restaurant", "tag-food"),
        (date(2026, 8, 22), 2_400, "Tramvia i metro", "cat-travel", "tag-transport"),
        (date(2026, 8, 22), 1_800, "Torre de Belém", "cat-leisure", "tag-visits"),
        (date(2026, 8, 22), 3_980, "Marisqueria", "cat-restaurant", "tag-food"),
        (date(2026, 8, 23), 2_600, "Museu del rajol", "cat-leisure", "tag-visits"),
        (date(2026, 8, 23), 5_240, "Sopar de comiat", "cat-restaurant", "tag-food"),
        (date(2026, 8, 24), 1_150, "Esmorzar", "cat-coffee", "tag-food"),
    ]
    for day, cents, name, category, tag in lisbon:
        demo.expense(cents, day, name, category, trip_id="trip-lisbon", tag_id=tag)
    demo.expense(14_000, date(2026, 9, 20), "Casa rural", "cat-travel", trip_id="trip-pyrenees", tag_id="tag-stay")

    # ---------------------------------------------------------------- the first day of this month
    demo.expense(85_000, TODAY, "Lloguer", "cat-home", template_id="tpl-rent")
    demo.expense(2_380, TODAY, "Mercat", "cat-groceries")
    demo.expense(240, TODAY, "Cafè", "cat-coffee")
    at = demo.stamp(TODAY, 9)
    insert(
        "account_contributions", id=demo.next_id("contrib"), shared_account_id="acc-home", direction="in",
        contributor_kind="user", source_account_id="acc-main", amount_cents=20_000, date=TODAY.isoformat(),
        name="Aportació mensual", created_at=at, updated_at=at,
    )


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
            (ROOT / "android/core/src/main/resources/shared_account_integrity.sql").read_text(
                encoding="utf-8"
            )
        )
        connection.execute(f"PRAGMA user_version = {SCHEMA_VERSION}")
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("BEGIN IMMEDIATE")
        build(connection)
        connection.execute("COMMIT")

        foreign_key_errors = connection.execute("PRAGMA foreign_key_check").fetchall()
        if foreign_key_errors:
            raise RuntimeError(f"Foreign-key validation failed: {foreign_key_errors}")

        movement_count = connection.execute(
            "SELECT COUNT(*) FROM v_movement_summary"
        ).fetchone()[0]
        if movement_count < 200:
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
