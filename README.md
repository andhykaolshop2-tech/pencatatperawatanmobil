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
python tools/check_project.py              # static checks, no SDK needed
./gradlew testDebugUnitTest                # unit tests of the core logic
./gradlew lintRelease                      # Android lint
./gradlew assembleDebug                    # debug APK
./gradlew assembleRelease bundleRelease    # release APK + bundle (needs keystore.properties, see below)
```

Release signing reads `keystore.properties` from the project root (template:
`keystore.properties.example`). That file, keystores, `.env` and API keys are git-ignored and
must never be committed — this repository is public.

With a phone (USB debugging) or an emulator connected:

```bash
python tools/device_test.py --apk app/build/outputs/apk/release/app-release.apk   # end-to-end UI test
python tools/take_screenshots.py     # real screenshots with example data, per listing language
python tools/frame_screenshots.py    # Play Store screenshots, 1080×1920
python tools/collect_release.py      # everything for the Play Console -> siap-upload/
```

Both device scripts wipe the data of the app on that device.

## Where things are

| Path | Content |
|---|---|
| `app/` | The Android app (Kotlin, Jetpack Compose) |
| `app/src/main/java/.../core/` | Pure logic with unit tests: units, statistics, CSV format, reminders |
| `play-store/listing/` | Store listing texts (en-US, en-GB, de-DE) |
| `play-store/graphics/` | App icon 512×512, feature graphic 1024×500, screenshot background, raw generated images |
| `play-store/screenshots/` | Play Store screenshots per language, the raw captures and the captions |
| `docs/privacy-policy.html` | Privacy policy (English + German); its marked part is pasted into a Blogger page |
| `docs/CATATAN-PC-BUILD.md` | Build notes: what is verified and what is not (Indonesian) |
| `docs/CONTEKAN-PLAY-CONSOLE.md` | Answers for the Play Console forms: Data safety, content rating, … (Indonesian) |
| `docs/demo-data/` | Example CSV files for screenshots and import tests |
| `tools/` | Python scripts: static checks, device test, screenshots, store graphics, release folder |

## Third-party software

The app uses Jetpack Compose / AndroidX, Kotlin, kotlinx.coroutines and Material icons, all
under the Apache License 2.0. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
