#!/usr/bin/env python3
"""Creates example log books (CSV) that can be imported into Odolog.

Use them on the build PC to fill the app with realistic data for the Play Store
screenshots, and to test the CSV import:

    python tools/make_demo_data.py            # writes docs/demo-data/*.csv, dated up to today
    -> copy a file to the phone / emulator, then in the app: Settings -> Import CSV file

The data is invented. The files use exactly the format the app exports
(see app/src/main/java/.../core/Backup.kt). Only the Python standard library is used.
"""

from __future__ import annotations

import argparse
import csv
import datetime
import pathlib
import random

ROOT = pathlib.Path(__file__).resolve().parent.parent
HEADER = [
    "record", "date", "odometer", "distance_unit", "quantity", "quantity_unit", "total_cost",
    "currency", "full", "missed_previous", "category", "title", "note", "repeat_months",
    "notify_days_before",
]

# One entry per demo file. Texts are in the language of the screenshots.
PROFILES = {
    "demo-en-US": dict(
        vehicle="Family car", powertrain="combustion", currency="USD", distance_unit="mi",
        quantity_unit="gal_us", start_odometer=26310, leg=(290, 380), economy=(30.0, 35.5), economy_kind="mpg",
        price=(3.09, 3.79), every_days=(9, 14),
        expenses=[
            (-395, "inspection", "State inspection", 35.00, ""),
            (-330, "insurance", "Car insurance", 742.00, "Annual premium"),
            (-290, "tires", "All-season tires", 486.40, "Set of four, fitted"),
            (-185, "service", "Oil change and filters", 119.90, ""),
            (-120, "cleaning", "Car wash", 14.00, ""),
            (-64, "repair", "Brake pads, front", 228.50, ""),
            (-31, "parking", "Airport parking", 42.00, ""),
            (-9, "cleaning", "Car wash", 14.00, ""),
        ],
        reminders=[
            (-2, "other", "Replace wiper blades", 0, 7, ""),
            (19, "inspection", "State inspection", 12, 30, "Book an appointment early"),
            (58, "other", "Insurance renewal", 12, 14, ""),
            (163, "service", "Annual service", 12, 14, "Oil, filters, brake check"),
        ],
        partial_note="Topped up on the road",
    ),
    "demo-en-GB": dict(
        vehicle="Family car", powertrain="combustion", currency="GBP", distance_unit="mi",
        quantity_unit="l", start_odometer=31480, leg=(280, 370), economy=(6.0, 7.2), economy_kind="l_100km",
        price=(1.36, 1.52), every_days=(9, 14),
        expenses=[
            (-346, "inspection", "MOT test", 54.85, "Passed, no advisories"),
            (-330, "insurance", "Car insurance", 512.00, "Annual premium"),
            (-290, "tires", "Two front tyres", 196.00, "Fitted and balanced"),
            (-185, "service", "Interim service", 149.00, ""),
            (-120, "cleaning", "Car wash", 9.00, ""),
            (-64, "repair", "Brake pads, front", 168.50, ""),
            (-31, "parking", "Station car park", 18.50, ""),
            (-9, "cleaning", "Car wash", 9.00, ""),
        ],
        reminders=[
            (-2, "other", "Replace wiper blades", 0, 7, ""),
            (19, "inspection", "MOT", 12, 30, "Book early"),
            (58, "other", "Insurance renewal", 12, 14, ""),
            (163, "service", "Annual service", 12, 14, "Oil, filters, brake check"),
        ],
        partial_note="Topped up on the motorway",
    ),
    "demo-de-DE": dict(
        vehicle="Familienauto", powertrain="combustion", currency="EUR", distance_unit="km",
        quantity_unit="l", start_odometer=42318, leg=(470, 620), economy=(6.1, 7.4), economy_kind="l_100km",
        price=(1.62, 1.89), every_days=(9, 14),
        expenses=[
            (-346, "inspection", "HU/AU", 149.50, "Ohne Mängel"),
            (-330, "insurance", "Kfz-Versicherung", 586.00, "Jahresbeitrag"),
            (-290, "tires", "Winterreifen", 428.00, "Vier Stück, montiert"),
            (-185, "service", "Inspektion mit Ölwechsel", 289.90, ""),
            (-120, "cleaning", "Waschanlage", 12.00, ""),
            (-64, "repair", "Bremsbeläge vorn", 198.50, ""),
            (-31, "parking", "Parkhaus Flughafen", 36.00, ""),
            (-9, "cleaning", "Waschanlage", 12.00, ""),
        ],
        reminders=[
            (-2, "other", "Wischerblätter tauschen", 0, 7, ""),
            (19, "inspection", "Hauptuntersuchung", 24, 30, "Termin früh buchen"),
            (58, "other", "Versicherung prüfen", 12, 14, ""),
            (163, "service", "Inspektion", 12, 14, "Öl, Filter, Bremsen"),
        ],
        partial_note="Unterwegs nachgetankt",
    ),
    "demo-ev-en-US": dict(
        vehicle="Electric car", powertrain="electric", currency="USD", distance_unit="mi",
        quantity_unit="kwh", start_odometer=8120, leg=(95, 170), economy=(26.0, 31.0), economy_kind="kwh_100mi",
        price=(0.15, 0.46), every_days=(4, 8),
        expenses=[
            (-330, "insurance", "Car insurance", 890.00, "Annual premium"),
            (-200, "tires", "Tire rotation", 45.00, ""),
            (-120, "cleaning", "Car wash", 14.00, ""),
            (-40, "service", "Cabin air filter", 68.00, ""),
        ],
        reminders=[
            (19, "inspection", "State inspection", 12, 30, ""),
            (58, "other", "Insurance renewal", 12, 14, ""),
            (163, "service", "Brake fluid check", 24, 14, ""),
        ],
        partial_note="Quick top-up at a fast charger",
    ),
    "demo-ev-de-DE": dict(
        vehicle="Elektroauto", powertrain="electric", currency="EUR", distance_unit="km",
        quantity_unit="kwh", start_odometer=12950, leg=(160, 270), economy=(15.5, 19.0), economy_kind="kwh_100km",
        price=(0.29, 0.59), every_days=(4, 8),
        expenses=[
            (-330, "insurance", "Kfz-Versicherung", 698.00, "Jahresbeitrag"),
            (-200, "tires", "Reifenwechsel", 49.00, ""),
            (-120, "cleaning", "Waschanlage", 12.00, ""),
            (-40, "service", "Innenraumfilter", 74.00, ""),
        ],
        reminders=[
            (19, "inspection", "Hauptuntersuchung", 24, 30, "Termin früh buchen"),
            (58, "other", "Versicherung prüfen", 12, 14, ""),
            (163, "service", "Bremsflüssigkeit prüfen", 24, 14, ""),
        ],
        partial_note="Kurz am Schnelllader nachgeladen",
    ),
}


def quantity_for(profile: dict, distance: float, rng: random.Random) -> float:
    """Fuel or energy used on `distance`, in the quantity unit of the profile."""
    value = rng.uniform(*profile["economy"])
    kind = profile["economy_kind"]
    if kind == "mpg":                       # miles per US gallon
        return distance / value
    if kind == "l_100km":                   # litres per 100 km (distance may be in miles)
        km = distance * 1.609344 if profile["distance_unit"] == "mi" else distance
        return km * value / 100.0
    return distance * value / 100.0         # kWh per 100 distance units


def build_rows(profile: dict, today: datetime.date, months: int, seed: int) -> list[list[str]]:
    rng = random.Random(seed)
    rows: list[dict] = []

    def add(**cells: str) -> None:
        rows.append(cells)

    add(record="vehicle", category=profile["powertrain"], title=profile["vehicle"])

    record = "charge" if profile["quantity_unit"] == "kwh" else "fuel"
    date = today - datetime.timedelta(days=months * 30)
    odometer = float(profile["start_odometer"])
    energy_rows: list[dict] = []
    pending = 0.0           # used since the last fill-up (carried over by a partial one)
    first = True
    while date <= today:
        distance = 0.0 if first else rng.uniform(*profile["leg"])
        odometer += distance
        used = pending + (quantity_for(profile, distance, rng) if not first else rng.uniform(28, 40))
        partial = (not first) and rng.random() < 0.08
        amount = used * 0.4 if partial else used
        pending = used - amount if partial else 0.0
        price = rng.uniform(*profile["price"])
        energy_rows.append(dict(
            record=record,
            date=date.isoformat(),
            odometer=f"{odometer:.0f}",
            distance_unit=profile["distance_unit"],
            quantity=f"{amount:.2f}",
            quantity_unit=profile["quantity_unit"],
            total_cost=f"{amount * price:.2f}",
            currency=profile["currency"],
            full="0" if partial else "1",
            missed_previous="0",
            note=profile["partial_note"] if partial else "",
        ))
        first = False
        date += datetime.timedelta(days=rng.randint(*profile["every_days"]))
    rows.extend(energy_rows)

    odometer_by_date = [(datetime.date.fromisoformat(r["date"]), r["odometer"]) for r in energy_rows]
    for offset, category, title, cost, note in profile["expenses"]:
        day = today + datetime.timedelta(days=offset)
        earlier = [odo for when, odo in odometer_by_date if when <= day]
        add(record="expense", date=day.isoformat(), odometer=earlier[-1] if earlier else "",
            distance_unit=profile["distance_unit"] if earlier else "", total_cost=f"{cost:.2f}",
            currency=profile["currency"], category=category, title=title, note=note)

    for offset, kind, title, repeat, notify, note in profile["reminders"]:
        day = today + datetime.timedelta(days=offset)
        add(record="reminder", date=day.isoformat(), category=kind, title=title, note=note,
            repeat_months=str(repeat), notify_days_before=str(notify))

    return [[cells.get(column, "") for column in HEADER] for cells in rows]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default=str(ROOT / "docs" / "demo-data"), help="output folder")
    parser.add_argument("--today", help="last day of the data, YYYY-MM-DD (default: today)")
    parser.add_argument("--months", type=int, default=14, help="how many months of history (default 14)")
    args = parser.parse_args()

    today = datetime.date.fromisoformat(args.today) if args.today else datetime.date.today()
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    for index, (name, profile) in enumerate(PROFILES.items()):
        rows = build_rows(profile, today, args.months, seed=20261111 + index)
        target = out / f"{name}.csv"
        # utf-8-sig = UTF-8 with byte order mark, like the app's own export
        with target.open("w", encoding="utf-8-sig", newline="") as handle:
            writer = csv.writer(handle, lineterminator="\r\n")
            writer.writerow(HEADER)
            writer.writerows(rows)
        print(f"wrote {target.relative_to(ROOT) if target.is_relative_to(ROOT) else target} ({len(rows)} rows)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
