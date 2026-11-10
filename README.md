# Odolog

Car maintenance and fuel log for Android — fill-ups, EV charging, services, costs and
date-based reminders. English and German. By PersonalizePro Labs.

**No account. No ads. No internet permission. Your data stays on your phone.**

## Features (version 1)

- Log fill-ups or charging sessions, and services / other expenses
- Reminders for services and inspections, based on dates
- Total cost, cost per km or mile, average consumption and two simple charts
- Units: km / miles, litres / US gallons / UK gallons, kWh, any currency
- CSV export and import, so the data survives a change of phone
- One vehicle; petrol, diesel, plug-in hybrid or fully electric

Planned for later updates: several vehicles, automatic backups, an optional one-time Pro purchase.

## Build

Requirements: Android Studio with JDK 17 or 21, Android SDK Platform 36.

```bash
python tools/check_project.py     # static checks, no SDK needed
./gradlew test                    # unit tests of the core logic
./gradlew assembleDebug           # debug APK
./gradlew bundleRelease           # release bundle (needs keystore.properties, see below)
```

Release signing reads `keystore.properties` from the project root (template:
`keystore.properties.example`). That file, keystores, `.env` and API keys are git-ignored and
must never be committed — this repository is public.

## Where things are

| Path | Content |
|---|---|
| `app/` | The Android app (Kotlin, Jetpack Compose) |
| `app/src/main/java/.../core/` | Pure logic with unit tests: units, statistics, CSV format, reminders |
| `play-store/listing/` | Store listing texts (en-US, de-DE, optional en-GB) |
| `play-store/graphics/` | App icon 512×512, feature graphic 1024×500, and the raw generated images |
| `docs/privacy-policy.html` | Privacy policy page (English + German) |
| `docs/CATATAN-PC-BUILD.md` | Build notes and the list of things that are not verified yet (Indonesian) |
| `docs/CONTEKAN-PLAY-CONSOLE.md` | Answers for the Play Console forms: Data safety, content rating, … (Indonesian) |
| `docs/demo-data/` | Example CSV files for screenshots and import tests |
| `tools/` | `generate_images.py` (store graphics), `make_demo_data.py`, `check_project.py` |

## Third-party software

The app uses Jetpack Compose / AndroidX, Kotlin, kotlinx.coroutines and Material icons, all
under the Apache License 2.0. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
