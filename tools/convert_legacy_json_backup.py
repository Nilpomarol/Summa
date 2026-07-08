#!/usr/bin/env python3
"""Convert a legacy Gestor Finances JSON export into a current .gfbackup file."""

from __future__ import annotations

import argparse
import json
import sqlite3
import sys
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from zoneinfo import ZoneInfo


ROOT = Path(__file__).resolve().parents[1]
VIEW_FILES = [
    "v_movement_shared.sql",
    "v_movement_summary.sql",
    "v_account_flow.sql",
    "v_account_balance.sql",
    "v_actual_expense.sql",
    "v_actual_income.sql",
    "v_person_balance.sql",
    "v_trip_actual_total.sql",
]
# Kept in sync by hand with AndroidBackupDatabaseInspector.kt's REQUIRED_APP_OBJECTS
# (android/app/src/main/java/com/gestorfinances/app/data/backup/AndroidBackupDatabaseInspector.kt),
# which is itself guarded against schema drift by AndroidBackupDatabaseInspectorTest.kt. If you
# add a table/view there, add it here too.
REQUIRED_APP_OBJECTS = {
    "meta",
    "accounts",
    "categories",
    "people",
    "trips",
    "tags",
    "templates",
    "budgets",
    "auto_cat_rules",
    "import_batches",
    "movements",
    "splits",
    "split_lines",
    "v_account_balance",
    "v_actual_expense",
    "v_actual_income",
    "v_person_balance",
    "v_account_flow",
    "v_movement_shared",
    "v_movement_summary",
    "v_trip_actual_total",
}
BACKUP_PREFIX = "gestor-finances-backup-v1"
BACKUP_EXTENSION = ".gfbackup"
LOCAL_ZONE = ZoneInfo("Europe/Madrid")
CENT = Decimal("0.01")

CATEGORY_ICON_MAP = {
    "tag": "receipt",
    "plane": "flight",
    "bus": "directions_bus",
    "gift": "card_giftcard",
    "briefcase": "business_center",
    "users": "group",
    "credit_card": "credit_card",
    "bike": "directions_bike",
    "cake": "cake",
    "sun": "beach_access",
    "zap": "bolt",
    "smartphone": "phone_android",
    "pizza": "fastfood",
    "trending_up": "savings",
    "coffee": "local_cafe",
    "ticket": "theater_comedy",
    "scissors": "content_cut",
    "car": "directions_car",
    "shopping_cart": "shopping_cart",
    "utensils": "restaurant",
    "heart": "volunteer_activism",
    "graduation_cap": "school",
    "wine": "local_bar",
    "shopping_bag": "shopping_bag",
    "gamepad": "sports_esports",
    "beer": "local_bar",
}
CATEGORY_ICON_KEYS = set(CATEGORY_ICON_MAP.values()) | {
    "home",
    "restaurant",
    "sports_esports",
    "payments",
    "school",
    "local_hospital",
    "flight",
    "phone_android",
    "fitness_center",
    "apartment",
    "weekend",
    "kitchen",
    "build",
    "local_cafe",
    "local_bar",
    "wine_bar",
    "train",
    "directions_bus",
    "local_taxi",
    "two_wheeler",
    "directions_bike",
    "local_gas_station",
    "shopping_bag",
    "storefront",
    "diamond",
    "medical_services",
    "local_pharmacy",
    "spa",
    "self_improvement",
    "movie",
    "music_note",
    "theater_comedy",
    "sports_soccer",
    "sports_basketball",
    "beach_access",
    "hotel",
    "luggage",
    "computer",
    "headphones",
    "camera_alt",
    "wifi",
    "menu_book",
    "science",
    "calculate",
    "work",
    "business_center",
    "receipt",
    "face",
    "content_cut",
    "child_care",
    "pets",
    "group",
    "volunteer_activism",
    "card_giftcard",
    "celebration",
    "bolt",
    "water_drop",
    "local_mall",
    "checkroom",
    "local_laundry_service",
    "cleaning_services",
    "park",
    "local_florist",
    "cake",
    "icecream",
    "liquor",
    "casino",
    "nightlife",
    "directions_boat",
    "ev_station",
    "local_parking",
    "hiking",
    "pool",
    "euro",
}
ACCOUNT_ICON_KEYS = {
    "account_balance",
    "payments",
    "savings",
    "trending_up",
    "credit_card",
    "wallet",
    "business",
    "receipt_long",
}
ACCOUNT_ICON_MAP = {
    "banknote": "payments",
    "piggy_bank": "savings",
    "credit_card": "credit_card",
    "wallet": "wallet",
}


@dataclass(frozen=True)
class LegacyTransaction:
    raw: dict
    amount_cents: int
    local_date: str
    created_at: str
    updated_at: str
    trip_id: str | None
    tag_id: str | None


@dataclass
class ImportStats:
    accounts: int = 0
    categories: int = 0
    people: int = 0
    trips: int = 0
    tags: int = 0
    movements: int = 0
    external_splits: int = 0
    movement_splits: int = 0
    skipped_deleted: int = 0
    skipped_zero_amount: int = 0
    skipped_invalid: int = 0
    legacy_recurring_flags_ignored: int = 0
    recurring_templates_ignored: int = 0
    assignment_rules_ignored: int = 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Convert a legacy JSON export into an Android whole-database .gfbackup.",
    )
    parser.add_argument("input", type=Path, help="Path to finances_backup_*.json")
    parser.add_argument(
        "--output",
        type=Path,
        help="Output .gfbackup path. Defaults to build/imported-backups/<generated-name>.",
    )
    parser.add_argument(
        "--snapshot-version",
        type=int,
        default=None,
        help="Snapshot version to write into meta and the filename. Defaults to YYYYMMDD0001 from exportDate.",
    )
    return parser.parse_args()


def read_sql(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def read_legacy_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle, parse_float=Decimal)


def parse_export_date(value: str | None) -> datetime:
    if not value:
        return datetime.now(timezone.utc)
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(timezone.utc)


def iso_utc(value: datetime) -> str:
    return value.astimezone(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def instant_from_legacy(value: object, fallback: datetime) -> datetime:
    if value is None or value == "":
        return fallback
    if isinstance(value, (int, float, Decimal)):
        return datetime.fromtimestamp(int(value) / 1000, tz=timezone.utc)
    if isinstance(value, str):
        stripped = value.strip()
        if not stripped:
            return fallback
        if stripped.isdigit():
            return datetime.fromtimestamp(int(stripped) / 1000, tz=timezone.utc)
        try:
            return datetime.fromisoformat(stripped.replace("Z", "+00:00")).astimezone(timezone.utc)
        except ValueError:
            return fallback
    return fallback


def date_from_legacy(value: object) -> str | None:
    if value is None or value == "":
        return None
    return instant_from_legacy(value, datetime.now(timezone.utc)).astimezone(LOCAL_ZONE).date().isoformat()


def local_date_from_legacy(value: object) -> str:
    date = date_from_legacy(value)
    if date is None:
        raise ValueError("Legacy row is missing a date")
    return date


def timestamp_filename(value: datetime) -> str:
    return value.astimezone(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def to_cents(value: object) -> int:
    if value is None or value == "":
        return 0
    amount = Decimal(str(value)).quantize(CENT, rounding=ROUND_HALF_UP)
    return int(amount * 100)


def euro_text(value: object) -> str:
    return f"{Decimal(to_cents(value)) / Decimal(100):.2f} EUR"


def optional_text(value: object) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def normalized_key(value: object) -> str:
    text = optional_text(value) or ""
    without_accents = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode("ascii")
    return without_accents.strip().lower().replace("-", "_").replace(" ", "_")


def truthy(value: object) -> bool:
    if isinstance(value, bool):
        return value
    if value is None:
        return False
    if isinstance(value, (int, Decimal)):
        return value != 0
    return normalized_key(value) in {"1", "true", "yes", "si"}


def active_rows(rows: list[dict], stats: ImportStats) -> list[dict]:
    active = []
    for row in rows:
        if truthy(row.get("eliminat")):
            stats.skipped_deleted += 1
        else:
            active.append(row)
    return active


def account_type(value: object) -> str:
    return {
        "banc": "bank",
        "efectiu": "cash",
        "estalvi": "savings",
        "inversio": "investment",
    }.get(normalized_key(value), "other")


def account_icon(value: object, db_type: str) -> str:
    key = normalized_key(value)
    if key.startswith("data:image"):
        key = ""
    normalized = ACCOUNT_ICON_MAP.get(key, key)
    if normalized in ACCOUNT_ICON_KEYS:
        return normalized
    return {
        "bank": "account_balance",
        "cash": "payments",
        "savings": "savings",
        "investment": "trending_up",
    }.get(db_type, "wallet")


def category_kind(value: object) -> str:
    return {
        "despesa": "expense",
        "ingres": "income",
    }.get(normalized_key(value), "both")


def category_nature(row: dict) -> str:
    return "fixed" if truthy(row.get("es_fix")) else "variable"


def category_icon(value: object) -> str | None:
    key = normalized_key(value)
    normalized = CATEGORY_ICON_MAP.get(key, key)
    return normalized if normalized in CATEGORY_ICON_KEYS else None


def trip_type(value: object) -> str:
    return {
        "viatge": "trip",
        "celebracio": "celebration",
        "festa": "celebration",
    }.get(normalized_key(value), "other")


def trip_icon(db_type: str) -> str:
    return {
        "trip": "flight",
        "celebration": "celebration",
    }.get(db_type, "receipt")


def backup_name(snapshot_version: int, created_at: datetime) -> str:
    return f"{BACKUP_PREFIX}-{snapshot_version:012d}-{timestamp_filename(created_at)}{BACKUP_EXTENSION}"


def default_snapshot_version(created_at: datetime) -> int:
    return int(created_at.strftime("%Y%m%d")) * 10_000 + 1


def prepare_output(args: argparse.Namespace, created_at: datetime, snapshot_version: int) -> Path:
    output = args.output
    if output is None:
        output = ROOT / "build" / "imported-backups" / backup_name(snapshot_version, created_at)
    if output.suffix.lower() != BACKUP_EXTENSION:
        output = output.with_suffix(BACKUP_EXTENSION)
    output.parent.mkdir(parents=True, exist_ok=True)
    for path in [output, Path(f"{output}-wal"), Path(f"{output}-shm")]:
        if path.exists():
            path.unlink()
    return output


def build_transactions(
    rows: list[dict],
    export_date: datetime,
    active_trip_ids: set[str],
    active_tag_ids: set[str],
    stats: ImportStats,
) -> list[LegacyTransaction]:
    transactions: list[LegacyTransaction] = []
    for row in rows:
        amount_cents = to_cents(row.get("import_trs", 0))
        if amount_cents <= 0:
            stats.skipped_zero_amount += 1
            continue
        if truthy(row.get("recurrent")):
            stats.legacy_recurring_flags_ignored += 1
        date_value = row.get("data")
        modified = instant_from_legacy(row.get("data_modificacio"), instant_from_legacy(date_value, export_date))
        trip_id = optional_text(row.get("esdeveniment_id"))
        if trip_id not in active_trip_ids:
            trip_id = None
        tag_id = optional_text(row.get("event_tag_id"))
        if tag_id not in active_tag_ids or trip_id is None:
            tag_id = None
        transactions.append(
            LegacyTransaction(
                raw=row,
                amount_cents=amount_cents,
                local_date=local_date_from_legacy(date_value),
                created_at=iso_utc(modified),
                updated_at=iso_utc(modified),
                trip_id=trip_id,
                tag_id=tag_id,
            ),
        )
    return transactions


def movement_flow_delta(tx: LegacyTransaction) -> tuple[str | None, int]:
    row = tx.raw
    kind = normalized_key(row.get("tipus"))
    account_id = optional_text(row.get("compte_id"))
    if kind == "ingres":
        return account_id, tx.amount_cents
    if kind == "despesa" and not optional_text(row.get("pagat_per_id")):
        return account_id, -tx.amount_cents
    if kind == "transferencia":
        return account_id, -tx.amount_cents
    return None, 0


def movement_destination_delta(tx: LegacyTransaction) -> tuple[str | None, int]:
    if normalized_key(tx.raw.get("tipus")) != "transferencia":
        return None, 0
    return optional_text(tx.raw.get("compte_desti_id")), tx.amount_cents


def account_transaction_counts(transactions: list[LegacyTransaction]) -> Counter:
    counts: Counter = Counter()
    for tx in transactions:
        for account_id, delta in [movement_flow_delta(tx), movement_destination_delta(tx)]:
            if account_id and delta:
                counts[account_id] += 1
    return counts


def account_deltas(transactions: list[LegacyTransaction]) -> Counter:
    deltas: Counter = Counter()
    for tx in transactions:
        for account_id, delta in [movement_flow_delta(tx), movement_destination_delta(tx)]:
            if account_id and delta:
                deltas[account_id] += delta
    return deltas


def group_split_rows(rows: list[dict]) -> dict[str, list[dict]]:
    grouped: dict[str, list[dict]] = defaultdict(list)
    for row in rows:
        tx_id = optional_text(row.get("transaccio_id"))
        if tx_id:
            grouped[tx_id].append(row)
    return dict(grouped)


def apply_schema(conn: sqlite3.Connection) -> None:
    conn.executescript(read_sql(ROOT / "shared" / "migrations" / "001_initial.sql"))
    for name in VIEW_FILES:
        conn.executescript(read_sql(ROOT / "shared" / "queries" / name))


def insert_accounts(
    conn: sqlite3.Connection,
    accounts: list[dict],
    deltas: Counter,
    counts: Counter,
    export_date: datetime,
    stats: ImportStats,
) -> set[str]:
    active_ids = {str(row["id"]) for row in accounts}
    default_id = max(active_ids, key=lambda account_id: (counts[account_id], account_id)) if active_ids else None
    for index, row in enumerate(accounts):
        account_id = str(row["id"])
        modified = instant_from_legacy(row.get("data_modificacio"), export_date)
        db_type = account_type(row.get("tipus"))
        starting_balance = to_cents(row.get("saldo", 0)) - deltas[account_id]
        conn.execute(
            """
            INSERT INTO accounts(
                id, name, starting_balance_cents, type, icon, color, is_default,
                display_order, low_balance_threshold_cents, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)
            """,
            (
                account_id,
                optional_text(row.get("nom")) or "Compte importat",
                starting_balance,
                db_type,
                account_icon(row.get("logo"), db_type),
                optional_text(row.get("color")),
                1 if account_id == default_id else 0,
                index,
                iso_utc(modified),
                iso_utc(modified),
            ),
        )
        stats.accounts += 1
    return active_ids


def insert_categories(
    conn: sqlite3.Connection,
    categories: list[dict],
    export_date: datetime,
    stats: ImportStats,
) -> set[str]:
    active_ids = set()
    for index, row in enumerate(categories):
        category_id = str(row["id"])
        active_ids.add(category_id)
        modified = instant_from_legacy(row.get("data_modificacio"), export_date)
        conn.execute(
            """
            INSERT INTO categories(
                id, name, kind, nature, parent_id, icon, color, display_order, created_at, updated_at
            ) VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?, ?)
            """,
            (
                category_id,
                optional_text(row.get("nom")) or "Categoria importada",
                category_kind(row.get("tipus")),
                category_nature(row),
                category_icon(row.get("icona")),
                optional_text(row.get("color")),
                index,
                iso_utc(modified),
                iso_utc(modified),
            ),
        )
        stats.categories += 1
    return active_ids


def insert_people(conn: sqlite3.Connection, people: list[dict], export_date: datetime, stats: ImportStats) -> set[str]:
    active_ids = set()
    for row in people:
        person_id = str(row["id"])
        active_ids.add(person_id)
        modified = instant_from_legacy(row.get("data_modificacio"), export_date)
        notes = []
        if truthy(row.get("amagat")):
            notes.append("Import antic: persona amagada a l'app antiga.")
        if to_cents(row.get("saldo_caixejat")) != 0:
            notes.append(f"Saldo antic caixejat: {euro_text(row.get('saldo_caixejat'))}.")
        conn.execute(
            """
            INSERT INTO people(id, name, avatar, color, notes, created_at, updated_at)
            VALUES (?, ?, NULL, NULL, ?, ?, ?)
            """,
            (
                person_id,
                optional_text(row.get("nom")) or "Persona importada",
                "\n".join(notes) if notes else None,
                iso_utc(modified),
                iso_utc(modified),
            ),
        )
        stats.people += 1
    return active_ids


def event_status(start_date: str | None, end_date: str | None, export_date: datetime) -> str:
    today = export_date.astimezone(LOCAL_ZONE).date()
    if start_date and datetime.fromisoformat(start_date).date() > today:
        return "planned"
    if end_date and datetime.fromisoformat(end_date).date() < today:
        return "finished"
    return "active"


def insert_trips(conn: sqlite3.Connection, events: list[dict], export_date: datetime, stats: ImportStats) -> set[str]:
    active_ids = set()
    for row in events:
        trip_id = str(row["id"])
        active_ids.add(trip_id)
        modified = instant_from_legacy(row.get("data_modificacio"), export_date)
        start_date = date_from_legacy(row.get("data_inici"))
        end_date = date_from_legacy(row.get("data_fi"))
        db_type = trip_type(row.get("tipus"))
        conn.execute(
            """
            INSERT INTO trips(
                id, name, type, status, start_date, end_date, icon, color, notes,
                default_account_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, ?, ?)
            """,
            (
                trip_id,
                optional_text(row.get("nom")) or "Esdeveniment importat",
                db_type,
                event_status(start_date, end_date, export_date),
                start_date,
                end_date,
                trip_icon(db_type),
                iso_utc(modified),
                iso_utc(modified),
            ),
        )
        stats.trips += 1
    return active_ids


def insert_tags(conn: sqlite3.Connection, rows: list[dict], export_date: datetime, stats: ImportStats) -> set[str]:
    active_ids = set()
    for row in rows:
        tag_id = str(row["id"])
        active_ids.add(tag_id)
        modified = instant_from_legacy(row.get("data_modificacio"), export_date)
        conn.execute(
            """
            INSERT INTO tags(id, name, icon, color, trip_id, category_id, trip_type, created_at, updated_at)
            VALUES (?, ?, ?, ?, NULL, NULL, ?, ?, ?)
            """,
            (
                tag_id,
                optional_text(row.get("nom")) or "Etiqueta importada",
                category_icon(row.get("icona")),
                optional_text(row.get("color")),
                trip_type(row.get("tipus_esdeveniment")),
                iso_utc(modified),
                iso_utc(modified),
            ),
        )
        stats.tags += 1
    return active_ids


def valid_category_id(row: dict, category_ids: set[str]) -> str | None:
    category_id = optional_text(row.get("categoria_id"))
    return category_id if category_id in category_ids else None


def insert_account_movement(
    conn: sqlite3.Connection,
    tx: LegacyTransaction,
    account_ids: set[str],
    category_ids: set[str],
    people_ids: set[str],
    stats: ImportStats,
) -> bool:
    row = tx.raw
    movement_id = str(row["id"])
    kind = normalized_key(row.get("tipus"))
    account_id = optional_text(row.get("compte_id"))
    if account_id not in account_ids:
        stats.skipped_invalid += 1
        return False

    settlement_person_id = optional_text(row.get("liquidacio_persona_id"))
    movement_type = {
        "ingres": "settlement" if settlement_person_id else "income",
        "despesa": "expense",
        "transferencia": "transfer",
    }.get(kind)
    if movement_type is None:
        stats.skipped_invalid += 1
        return False
    if movement_type == "settlement" and settlement_person_id not in people_ids:
        stats.skipped_invalid += 1
        return False

    dest_account_id = optional_text(row.get("compte_desti_id")) if movement_type == "transfer" else None
    if movement_type == "transfer" and dest_account_id not in account_ids:
        stats.skipped_invalid += 1
        return False

    category_id = valid_category_id(row, category_ids) if movement_type in {"expense", "income"} else None
    keep_trip_fields = movement_type in {"expense", "income"}
    conn.execute(
        """
        INSERT INTO movements(
            id, type, amount_cents, date, account_id, dest_account_id, name, payee, notes,
            is_one_time, category_id, tag_id, trip_id, template_id, import_batch_id, person_id,
            settlement_direction, refunds_expense_id, actual_refund_cents, created_at, updated_at
        ) VALUES (
            ?, ?, ?, ?, ?, ?, ?, NULL, ?, 0, ?, ?, ?, NULL, NULL, ?, ?, NULL, NULL, ?, ?
        )
        """,
        (
            movement_id,
            movement_type,
            tx.amount_cents,
            tx.local_date,
            account_id,
            dest_account_id,
            optional_text(row.get("concepte")),
            optional_text(row.get("notes")),
            category_id,
            tx.tag_id if keep_trip_fields else None,
            tx.trip_id if keep_trip_fields else None,
            settlement_person_id if movement_type == "settlement" else None,
            "person_to_user" if movement_type == "settlement" else None,
            tx.created_at,
            tx.updated_at,
        ),
    )
    stats.movements += 1
    return True


def insert_movement_split(
    conn: sqlite3.Connection,
    tx: LegacyTransaction,
    rows: list[dict],
    people_ids: set[str],
    stats: ImportStats,
) -> None:
    split_lines: list[tuple[str, int]] = []
    for row in rows:
        person_id = optional_text(row.get("persona_id"))
        if person_id not in people_ids:
            stats.skipped_invalid += 1
            return
        split_lines.append((person_id, to_cents(row.get("import_degut"))))
    person_total = sum(amount for _, amount in split_lines)
    if person_total > tx.amount_cents:
        stats.skipped_invalid += 1
        return
    user_share = tx.amount_cents - person_total
    split_id = f"split-{tx.raw['id']}"
    conn.execute(
        """
        INSERT INTO splits(id, movement_id, payer_person_id, entry_method, total_amount_cents, date,
            description, category_id, trip_id, tag_id, created_at, updated_at)
        VALUES (?, ?, NULL, 'exact', NULL, NULL, NULL, NULL, NULL, NULL, ?, ?)
        """,
        (split_id, str(tx.raw["id"]), tx.created_at, tx.updated_at),
    )
    conn.execute(
        """
        INSERT INTO split_lines(id, split_id, participant_kind, person_id, owed_amount_cents, owed_percent, created_at, updated_at)
        VALUES (?, ?, 'user', NULL, ?, NULL, ?, ?)
        """,
        (f"{split_id}-user", split_id, user_share, tx.created_at, tx.updated_at),
    )
    for index, (person_id, amount_cents) in enumerate(split_lines, start=1):
        conn.execute(
            """
            INSERT INTO split_lines(id, split_id, participant_kind, person_id, owed_amount_cents, owed_percent, created_at, updated_at)
            VALUES (?, ?, 'person', ?, ?, NULL, ?, ?)
            """,
            (f"{split_id}-person-{index}", split_id, person_id, amount_cents, tx.created_at, tx.updated_at),
        )
    stats.movement_splits += 1


def insert_external_split(
    conn: sqlite3.Connection,
    tx: LegacyTransaction,
    category_ids: set[str],
    people_ids: set[str],
    stats: ImportStats,
) -> bool:
    row = tx.raw
    payer_id = optional_text(row.get("pagat_per_id"))
    if payer_id not in people_ids:
        stats.skipped_invalid += 1
        return False
    split_id = f"external-{row['id']}"
    conn.execute(
        """
        INSERT INTO splits(id, movement_id, payer_person_id, entry_method, total_amount_cents, date,
            description, category_id, trip_id, tag_id, created_at, updated_at)
        VALUES (?, NULL, ?, 'exact', ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            split_id,
            payer_id,
            tx.amount_cents,
            tx.local_date,
            optional_text(row.get("concepte")),
            valid_category_id(row, category_ids),
            tx.trip_id,
            tx.tag_id,
            tx.created_at,
            tx.updated_at,
        ),
    )
    conn.execute(
        """
        INSERT INTO split_lines(id, split_id, participant_kind, person_id, owed_amount_cents, owed_percent, created_at, updated_at)
        VALUES (?, ?, 'user', NULL, ?, NULL, ?, ?)
        """,
        (f"{split_id}-user", split_id, tx.amount_cents, tx.created_at, tx.updated_at),
    )
    stats.external_splits += 1
    return True


def insert_transactions(
    conn: sqlite3.Connection,
    transactions: list[LegacyTransaction],
    split_groups: dict[str, list[dict]],
    account_ids: set[str],
    category_ids: set[str],
    people_ids: set[str],
    stats: ImportStats,
) -> None:
    for tx in sorted(transactions, key=lambda item: (item.local_date, item.created_at, str(item.raw.get("id")))):
        kind = normalized_key(tx.raw.get("tipus"))
        if kind == "despesa" and optional_text(tx.raw.get("pagat_per_id")):
            insert_external_split(conn, tx, category_ids, people_ids, stats)
            continue
        if insert_account_movement(conn, tx, account_ids, category_ids, people_ids, stats):
            if kind == "despesa" and str(tx.raw["id"]) in split_groups:
                insert_movement_split(conn, tx, split_groups[str(tx.raw["id"])], people_ids, stats)


def set_snapshot_version(conn: sqlite3.Connection, snapshot_version: int) -> None:
    conn.execute(
        "UPDATE meta SET value = ? WHERE key = 'snapshot_version'",
        (str(snapshot_version),),
    )


def validate_database(path: Path) -> dict[str, int]:
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    try:
        integrity = conn.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise RuntimeError(f"integrity_check failed: {integrity}")
        objects = {
            row["name"]
            for row in conn.execute("SELECT name FROM sqlite_master WHERE type IN ('table', 'view')")
        }
        missing = REQUIRED_APP_OBJECTS - objects
        if missing:
            raise RuntimeError(f"missing app objects: {', '.join(sorted(missing))}")
        counts = {
            "accounts": conn.execute("SELECT COUNT(*) FROM accounts").fetchone()[0],
            "categories": conn.execute("SELECT COUNT(*) FROM categories").fetchone()[0],
            "people": conn.execute("SELECT COUNT(*) FROM people").fetchone()[0],
            "trips": conn.execute("SELECT COUNT(*) FROM trips").fetchone()[0],
            "tags": conn.execute("SELECT COUNT(*) FROM tags").fetchone()[0],
            "templates": conn.execute("SELECT COUNT(*) FROM templates").fetchone()[0],
            "movements": conn.execute("SELECT COUNT(*) FROM movements").fetchone()[0],
            "splits": conn.execute("SELECT COUNT(*) FROM splits").fetchone()[0],
            "actual_expense_rows": conn.execute("SELECT COUNT(*) FROM v_actual_expense").fetchone()[0],
            "person_balance_rows": conn.execute("SELECT COUNT(*) FROM v_person_balance").fetchone()[0],
        }
        conn.execute("SELECT COUNT(*) FROM v_account_balance").fetchone()
        conn.execute("SELECT COUNT(*) FROM v_actual_income").fetchone()
        conn.execute("SELECT COUNT(*) FROM v_account_flow").fetchone()
        return counts
    finally:
        conn.close()


def finalize_single_file_database(conn: sqlite3.Connection, path: Path) -> None:
    conn.commit()
    conn.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    conn.execute("PRAGMA journal_mode=DELETE")
    conn.close()
    for sidecar in [Path(f"{path}-wal"), Path(f"{path}-shm")]:
        if sidecar.exists():
            sidecar.unlink()


def convert(input_path: Path, output_path: Path, snapshot_version: int, export_date: datetime) -> tuple[ImportStats, dict[str, int]]:
    payload = read_legacy_json(input_path)
    data = payload.get("data") or {}
    stats = ImportStats()
    accounts = active_rows(data.get("accounts") or [], stats)
    categories = active_rows(data.get("categories") or [], stats)
    people = active_rows(data.get("people") or [], stats)
    events = active_rows(data.get("events") or [], stats)
    event_tags = active_rows(data.get("event_tags") or [], stats)
    transaction_rows = active_rows(data.get("transactions") or [], stats)
    transaction_splits = active_rows(data.get("transaction_splits") or [], stats)
    stats.recurring_templates_ignored = len(active_rows(data.get("recurring_templates") or [], stats))
    stats.assignment_rules_ignored = len(data.get("assignment_rules") or [])

    trip_ids = {str(row["id"]) for row in events}
    tag_ids = {str(row["id"]) for row in event_tags}
    transactions = build_transactions(transaction_rows, export_date, trip_ids, tag_ids, stats)
    split_groups = group_split_rows(transaction_splits)
    deltas = account_deltas(transactions)
    counts = account_transaction_counts(transactions)

    conn = sqlite3.connect(output_path)
    try:
        conn.execute("PRAGMA foreign_keys = ON")
        apply_schema(conn)
        with conn:
            account_ids = insert_accounts(conn, accounts, deltas, counts, export_date, stats)
            category_ids = insert_categories(conn, categories, export_date, stats)
            people_ids = insert_people(conn, people, export_date, stats)
            insert_trips(conn, events, export_date, stats)
            insert_tags(conn, event_tags, export_date, stats)
            insert_transactions(conn, transactions, split_groups, account_ids, category_ids, people_ids, stats)
            set_snapshot_version(conn, snapshot_version)
        finalize_single_file_database(conn, output_path)
    except Exception:
        conn.close()
        raise
    return stats, validate_database(output_path)


def main() -> int:
    args = parse_args()
    payload = read_legacy_json(args.input)
    export_date = parse_export_date(payload.get("exportDate"))
    snapshot_version = args.snapshot_version or default_snapshot_version(export_date)
    output = prepare_output(args, export_date, snapshot_version)
    stats, validation_counts = convert(args.input, output, snapshot_version, export_date)

    print(f"Created backup: {output}")
    print(f"Snapshot version: {snapshot_version}")
    print(
        "Imported rows: "
        f"{stats.accounts} accounts, {stats.categories} categories, {stats.people} people, "
        f"{stats.trips} trips, {stats.tags} tags, {stats.movements} movements, "
        f"{stats.movement_splits} shared splits, {stats.external_splits} external splits."
    )
    if stats.recurring_templates_ignored or stats.assignment_rules_ignored or stats.legacy_recurring_flags_ignored:
        print(
            "Ignored legacy automation: "
            f"{stats.recurring_templates_ignored} recurring templates, "
            f"{stats.legacy_recurring_flags_ignored} recurring movement flags, "
            f"{stats.assignment_rules_ignored} assignment rules."
        )
    if stats.skipped_zero_amount or stats.skipped_invalid or stats.skipped_deleted:
        print(
            "Skipped rows: "
            f"{stats.skipped_zero_amount} zero-amount, "
            f"{stats.skipped_invalid} invalid, "
            f"{stats.skipped_deleted} deleted."
        )
    print(
        "Validated SQLite: "
        f"{validation_counts['accounts']} accounts, "
        f"{validation_counts['categories']} categories, "
        f"{validation_counts['people']} people, "
        f"{validation_counts['trips']} trips, "
        f"{validation_counts['tags']} tags, "
        f"{validation_counts['templates']} templates, "
        f"{validation_counts['movements']} movements, "
        f"{validation_counts['splits']} splits."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"legacy JSON conversion failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
