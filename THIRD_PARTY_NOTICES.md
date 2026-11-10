# Third-party notices

Odolog contains or is built with the following third-party software. None of it is
under a copyleft licence such as GPL or AGPL.

| Component | Used for | Copyright | Licence |
|---|---|---|---|
| Jetpack Compose (ui, foundation, material3) and other AndroidX libraries they depend on | User interface | The Android Open Source Project | Apache License 2.0 |
| AndroidX Activity (`activity-compose`) | Activity, file picker and permission contracts | The Android Open Source Project | Apache License 2.0 |
| Kotlin standard library | Language runtime | JetBrains s.r.o. and Kotlin Programming Language contributors | Apache License 2.0 |
| kotlinx.coroutines | Background work, observable state | JetBrains s.r.o. and contributors | Apache License 2.0 |
| Material icons (path data in `app/src/main/res/drawable/ic_*.xml`) | Icons | Google LLC | Apache License 2.0 |
| JUnit 4 (tests only, not part of the app) | Unit tests | JUnit contributors | Eclipse Public License 1.0 |
| Gradle wrapper (`gradlew`, `gradle/wrapper/`) | Build tool launcher, not part of the app | Gradle, Inc. and contributors | Apache License 2.0 |

The full text of the Apache License 2.0 is included in the app
(`app/src/main/res/raw/apache_license_2_0.txt`, shown under *Settings → Open-source licenses*)
and is available at <https://www.apache.org/licenses/LICENSE-2.0>.

The app icon and the feature graphic in `play-store/graphics/` were generated for this
project with an AI image service (see `tools/generate_images.py`).
