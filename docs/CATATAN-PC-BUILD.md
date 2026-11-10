# Catatan untuk PC build — Odolog

Source code ini ditulis di PC yang **tidak menjalankan build Gradle sama sekali** (sesuai
instruksi). Dokumen ini menjelaskan cara build di PC build, apa yang sudah diperiksa, dan —
yang terpenting — apa yang **belum** terverifikasi.

## 1. Ringkasan proyek

| Hal | Nilai |
|---|---|
| Nama app | Odolog |
| applicationId / namespace | `com.personalizeprolabs.odolog` (**permanen** setelah upload pertama ke Play) |
| minSdk / targetSdk / compileSdk | 26 (Android 8.0) / 36 / 36 |
| Versi | versionCode 1, versionName 1.0.0 (`app/build.gradle.kts`) |
| Bahasa | Kotlin 2.0.21, UI Jetpack Compose (Material 3) |
| Build | Gradle 8.13 (wrapper), Android Gradle Plugin 8.13.0 |
| Dependensi | Compose BOM 2025.02.00 (ui, foundation, material3), activity-compose 1.9.3, kotlinx-coroutines-android 1.9.0, JUnit 4.13.2 (test) |
| Lisensi dependensi | semuanya Apache 2.0 (JUnit: EPL 1.0, hanya untuk test) — tidak ada AGPL/GPL |
| Izin | `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` — **tanpa** `INTERNET` |

Semua nomor versi ada di satu file: `gradle/libs.versions.toml`. Kombinasi AGP 8.13.0 +
Kotlin 2.0.21 + Gradle 8.13 adalah kombinasi template "New Project" Android Studio Narwhal.
Semua versi sudah saya cek **ada** di Google Maven / Maven Central, tetapi belum pernah
di-resolve oleh Gradle.

Struktur:

```
app/src/main/java/com/personalizeprolabs/odolog/
  core/        logika murni (tanpa Android): model, satuan, statistik, CSV, pengingat, format
  data/        SQLite (AppDatabase), Repository, SettingsStore (SharedPreferences)
  reminders/   alarm harian tidak-eksak + notifikasi
  ui/          semua layar Compose
app/src/test/  unit test untuk paket core (JUnit 4)
tools/         generate_images.py, make_demo_data.py, check_project.py
play-store/    teks listing + grafik
docs/          kebijakan privasi, contekan Play Console, data contoh, catatan ini
```

## 2. Yang dibutuhkan di PC build

- **Android Studio** yang mendukung AGP 8.13 (Narwhal 3 Feature Drop | 2025.1.3 atau lebih baru).
- **JDK 17 atau 21** untuk menjalankan Gradle. Pakai JDK bawaan Android Studio (JBR 21):
  *Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK*.
  Gradle 8.13 **tidak** jalan di Java 24 ke atas ("Unsupported class file major version").
  Untuk baris perintah: set `JAVA_HOME` ke folder `jbr` di dalam Android Studio.
- **Android SDK Platform 36** (SDK Manager). Build-Tools versi yang dibutuhkan diunduh sendiri
  oleh AGP kalau lisensi SDK sudah disetujui.
- Koneksi internet untuk build pertama (Gradle mengunduh dependensi ± beberapa ratus MB).
- Python 3.10+ hanya untuk script di `tools/` (opsional untuk build).

## 3. Langkah build

```bash
git clone https://github.com/andhykaolshop2-tech/pencatatperawatanmobil.git
cd pencatatperawatanmobil

python tools/check_project.py        # pemeriksaan statis, harus "0 error(s)"
./gradlew test                       # unit test logika inti (55 test)
./gradlew assembleDebug              # APK debug: app/build/outputs/apk/debug/app-debug.apk
./gradlew lint                       # laporan: app/build/reports/lint-results-debug.html
```

Di Windows pakai `gradlew.bat` (atau `.\gradlew`). Atau buka folder proyek di Android Studio,
tunggu Gradle sync, lalu Run.

### Build rilis (AAB untuk Play)

1. Buat *upload keystore* **di luar folder repo** (sekali saja, simpan baik-baik + cadangkan):
   ```bash
   keytool -genkeypair -v -keystore ../keys/odolog-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Salin `keystore.properties.example` menjadi `keystore.properties` (di root proyek) dan isi.
   File ini dan `*.jks` sudah ada di `.gitignore` — **jangan pernah di-commit, repo ini publik.**
3. Build:
   ```bash
   ./gradlew bundleRelease            # app/build/outputs/bundle/release/app-release.aab
   ./gradlew assembleRelease          # APK rilis untuk dicoba di HP sebelum upload
   ```
   Tanpa `keystore.properties`, build rilis tetap jalan tetapi hasilnya tidak ditandatangani.
4. **Coba APK rilis di HP sungguhan** sebelum upload. Build rilis memakai R8 (minify + shrink
   resources) dan itu belum pernah dijalankan. Kalau app rilis crash padahal debug normal,
   solusi cepat: `isMinifyEnabled = false` dan `isShrinkResources = false` di `app/build.gradle.kts`.

Untuk rilis berikutnya naikkan `versionCode` (wajib, +1) dan `versionName`.

## 4. Status verifikasi

### Sudah diperiksa (di PC penulisan, tanpa Gradle)

| Apa | Cara | Hasil |
|---|---|---|
| Logika inti (`core/`): satuan, statistik konsumsi, CSV, pengingat, parsing angka | 55 unit test dijalankan dengan compiler Kotlin bawaan Android Studio (2.1.10, language level 2.0) dan pengganti JUnit sederhana | semua lulus |
| `data/`, `reminders/`, `OdologApp` | pemeriksaan tipe compiler terhadap `android.jar` API 36 asli | 0 error |
| Seluruh kode termasuk layar Compose | front-end compiler Kotlin + plugin Compose terhadap **tiruan** tanda tangan API Compose/AndroidX yang saya tulis dari ingatan | 0 error |
| Resource XML, string EN/DE lengkap & placeholder sama, referensi `R.*` dan `@…/…`, path ikon vektor, panjang teks listing | `python tools/check_project.py` | 0 error |
| File CSV contoh | diimpor dengan importer asli app, lalu ekspor–impor ulang | semua baris terbaca |
| Gradle wrapper | dibuat dengan Gradle 8.13 resmi; SHA-256 `gradle-wrapper.jar` dan distribusi cocok dengan checksum resmi gradle.org | cocok |
| Icon & feature graphic | dilihat langsung; pratinjau mask lingkaran/squircle untuk icon launcher | baik |
| Halaman kebijakan privasi | dirender di browser (lebar HP) | baik |

### BELUM terverifikasi — periksa di PC build

1. **Build Gradle belum pernah dijalankan.** Resolusi dependensi, konfigurasi plugin, dan
   `app/build.gradle.kts` belum teruji. Bagian yang paling mungkin perlu disentuh:
   blok `tasks.withType<KotlinCompile>` (target JVM 17) dan blok `signingConfigs`.
2. **Pemakaian API Compose / Material 3 hanya dicek terhadap tiruan**, bukan library asli.
   Kalau ada error kompilasi, kemungkinan besar di salah satu titik ini (semuanya perbaikan
   satu baris):
   - `Modifier.consumeWindowInsets(innerPadding)` di `EntryEditScreen.kt` / `ReminderEditScreen.kt` — boleh dihapus;
   - `DatePickerDialog` / `rememberDatePickerState` / `DatePicker` di `ui/Common.kt`;
   - `Card(onClick = …)` di `RemindersTab.kt`, `FilterChip`, `HorizontalDivider`;
   - nama parameter `lightColorScheme(...)` / `darkColorScheme(...)` di `ui/Theme.kt`
     (`surfaceContainer*` butuh Material 3 ≥ 1.2);
   - `OutlinedTextField(suffix = …, supportingText = …, minLines = …)`;
   - `KeyboardOptions(capitalization = …)`.
3. **Resource belum dikompilasi `aapt2`** dan manifest belum di-merge. Perhatikan khusus:
   `res/xml/backup_rules.xml` dan `res/xml/data_extraction_rules.xml` (pemeriksaan lint
   *FullBackupContent* / *DataExtractionRules*), `res/xml/locales_config.xml`, icon adaptif.
4. **Lint belum dijalankan.** Peringatan yang sudah saya duga dan tidak berbahaya: konstanta API
   33 (`POST_NOTIFICATIONS`, `ACTION_APP_LOCALE_SETTINGS`) yang dipakai di balik pengecekan versi,
   `getPackageInfo` yang deprecated, atribut manifest yang hanya berlaku di API baru.
5. **Tidak ada yang pernah dijalankan di perangkat.** Semua perilaku runtime belum teruji:
   tampilan tiap layar (terang/gelap, bahasa Jerman, huruf besar, layar kecil), edge-to-edge
   dan keyboard di Android 15/16, rotasi layar, pemilih tanggal, pemilih file ekspor/impor,
   alarm → notifikasi (termasuk setelah restart HP dan dalam mode Doze), izin notifikasi
   Android 13+, build rilis dengan R8.
6. **Aturan backup** (cloud backup mati, transfer HP-ke-HP hidup) hanya ditulis menurut
   dokumentasi; belum diuji dengan `adb shell bmgr`.
7. **Daftar izin di manifest hasil merge** belum dilihat. Harus hanya `POST_NOTIFICATIONS`,
   `RECEIVE_BOOT_COMPLETED`, dan izin internal AndroidX `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
   Cek: *Build → Analyze APK…* atau `app/build/intermediates/merged_manifests/`.
   **Kalau ada `INTERNET`, klaim "tanpa internet" jadi salah — jangan rilis sebelum beres.**
8. **Teks formulir Play Console** di `docs/CONTEKAN-PLAY-CONSOLE.md` ditulis tanpa membuka Play
   Console; bunyi pertanyaan bisa berbeda.
9. **Kebijakan privasi** masih berisi placeholder `[[CONTACT_EMAIL]]` dan `[[POSTAL_ADDRESS]]`.

## 5. Daftar tes manual (disarankan sebelum rilis)

Pertama kali buka app:
- [ ] Layar "Welcome" muncul; satuan default sesuai negara HP (DE: km, L, L/100 km, EUR; UK: mil, liter, mpg UK, GBP; US: mil, galon, mpg, USD)
- [ ] Ganti jenis penggerak ke *Electric* → menu tambah hanya menawarkan "Charge" + "Service or expense"

Entri:
- [ ] Tambah 3 pengisian penuh dengan odometer naik → konsumsi muncul di entri ke-2 dan ke-3, di Overview dan Statistics
- [ ] Ketik angka dengan koma dan titik (`45,5` / `45.5`, `45.210` / `45,210`) → terbaca benar sesuai bahasa HP
- [ ] Pengisian sebagian (hapus centang "Full tank") dijumlahkan ke interval berikutnya
- [ ] Edit dan hapus entri; putar layar saat mengisi formulir (isi tidak hilang)
- [ ] Odometer lebih kecil dari entri sebelumnya → muncul peringatan, tetap bisa disimpan

Pengingat:
- [ ] Simpan pengingat pertama di Android 13+ → dialog izin notifikasi muncul
- [ ] Buat pengingat jatuh tempo hari ini, ubah jam HP melewati 09:00 (atau tunggu) → notifikasi muncul; ketuk → app terbuka di tab Reminders
- [ ] Restart HP → pengingat tetap bekerja keesokan harinya
- [ ] "Done" pada pengingat berulang → tanggal maju; pada yang tidak berulang → dihapus setelah konfirmasi
- [ ] Matikan notifikasi app di pengaturan sistem → kartu peringatan muncul di tab Reminders

Data:
- [ ] Export CSV → file bisa dibuka di Excel/Sheets; huruf umlaut benar
- [ ] Delete all data → Import file tadi → semua kembali
- [ ] Import file yang sama dua kali dengan "Add to my data" → tidak ada duplikat
- [ ] Import file bukan CSV → pesan "Cannot import this file", app tidak crash
- [ ] Import `docs/demo-data/*.csv`

Lain-lain:
- [ ] Bahasa Jerman lengkap (HP berbahasa Jerman, atau Settings → App language di Android 13+)
- [ ] Mode gelap; ukuran huruf sistem terbesar; layar kecil (≤ 5")
- [ ] Mode pesawat: semua fungsi tetap jalan
- [ ] Privacy → "Full policy" membuka halaman kebijakan privasi
- [ ] Icon launcher terlihat benar (bentuk lingkaran dan squircle)

## 6. Screenshot untuk Play Store

Dibuat di PC build dari app asli:

1. Jalankan app di emulator/HP (resolusi mis. 1080×2400).
2. Buat data contoh yang tanggalnya segar: `python tools/make_demo_data.py`, salin salah satu file
   dari `docs/demo-data/` ke perangkat (`adb push docs/demo-data/demo-de-DE.csv /sdcard/Download/`),
   lalu di app: *Settings → Import CSV file*.
   - `demo-en-US.csv` (USD, mil, galon) dan `demo-ev-en-US.csv` (mobil listrik) → listing en-US
   - `demo-en-GB.csv` (GBP, mil, liter) → listing en-GB
   - `demo-de-DE.csv` dan `demo-ev-de-DE.csv` → listing de-DE
3. Sesuaikan bahasa HP (Inggris / Jerman) dan satuan di Settings agar cocok dengan file.
4. Layar yang layak diambil: Overview, Log, Statistics (grafik), Reminders, formulir "New fill-up",
   dan Settings → Privacy. Untuk bukti dukungan mobil listrik: Overview/Statistics dari data `demo-ev-*`.
5. Ambil gambar: `adb exec-out screencap -p > shot.png` atau tombol kamera di emulator.
   Syarat Play: 2–8 screenshot, PNG/JPG, sisi 320–3840 px, rasio 16:9 atau 9:16.

## 7. Script gambar (icon & feature graphic)

Hasilnya sudah ada di repo, jadi **tidak perlu dijalankan lagi**:

- `play-store/graphics/icon-512.png`, `play-store/graphics/feature-graphic-1024x500.png`
- `play-store/graphics/raw/` — semua hasil asli dari API + `jobs-state.json`
- `app/src/main/res/mipmap-*/ic_launcher_foreground.png`, `res/values/ic_launcher_background.xml`

Kalau ingin varian baru di PC build:

```bash
python -m venv .venv
.venv/Scripts/python -m pip install -r tools/requirements.txt     # Pillow
# salin .env.example menjadi .env dan isi GEMINIGEN_API_KEY  (.env tidak pernah di-commit)
.venv/Scripts/python tools/generate_images.py                # tidak membuat ulang yang sudah ada
.venv/Scripts/python tools/generate_images.py --new icon     # satu varian icon tambahan (pakai kredit)
.venv/Scripts/python tools/generate_images.py --select icon icon-01-8ecb1a2a.png   # kembali ke varian lama
.venv/Scripts/python tools/generate_images.py --process-only # hanya membuat ulang file turunan
```

Cara kerja tanpa webhook: script mengirim permintaan, menerima `uuid`, lalu memeriksa
`GET /uapi/v1/history/{uuid}` berkala sampai selesai dan mengunduh hasilnya. Model yang dipakai:
GPT Image 2, mode `low`, resolusi `4K` (icon 2880×2880, feature 3840×2160; 4 kredit per gambar).

## 8. Keputusan desain yang perlu diketahui

- **Tanpa izin INTERNET.** Tautan kebijakan privasi dibuka lewat browser HP (intent), jadi app
  sendiri tetap tidak butuh internet.
- **Cloud backup Android dimatikan**, transfer langsung HP-ke-HP diizinkan
  (`res/xml/data_extraction_rules.xml`). Ini supaya klaim "data hanya di HP" benar secara harfiah.
  Jalur resmi ganti HP di v1 adalah ekspor/impor CSV.
- **Pengingat memakai alarm tidak-eksak** (`AlarmManager.setAndAllowWhileIdle`), dicek sekali
  sehari sekitar pukul 09:00; tidak butuh izin exact alarm. Android boleh menggesernya beberapa jam.
  Dijadwalkan ulang saat app dibuka, setelah restart HP, dan setelah update app.
- **Satu kendaraan** di v1. Tabel database sudah punya kolom `vehicle_id`, jadi multi-kendaraan
  bisa ditambahkan tanpa membongkar tabel.
- **Format CSV** didokumentasikan di `core/Backup.kt`. Kode enum (`fuel`, `charge`, `service`, …)
  disimpan di database dan CSV pengguna: jangan diubah, hanya boleh ditambah.
- **Tanpa library navigasi, DI, Room, DataStore, WorkManager, atau ikon**: sengaja, supaya
  dependensi minimal. Ikon adalah vektor Material (Apache 2.0) di `res/drawable`.
- **Bahasa**: default Inggris (ejaan AS), Jerman lengkap, plus penyesuaian kecil untuk en-GB,
  en-AU, en-CA. Menambah string baru: tambahkan di `values/strings.xml` **dan**
  `values-de/strings.xml`, lalu jalankan `python tools/check_project.py`.

## 9. Kalau build gagal

| Gejala | Kemungkinan penyebab / solusi |
|---|---|
| `Unsupported class file major version …` | Gradle dijalankan dengan JDK terlalu baru. Pakai JDK 17/21 (JBR Android Studio). |
| `SDK location not found` | Buka proyek sekali di Android Studio, atau buat `local.properties` berisi `sdk.dir=…` (file ini di-ignore git). |
| `Failed to install … licences have not been accepted` | Setujui lisensi di SDK Manager Android Studio. |
| `Could not resolve …` | Tidak ada internet, atau proxy. Versi dependensi sendiri sudah dicek ada. |
| Android Studio menawarkan upgrade AGP/Kotlin | Boleh ditolak. Kalau mau upgrade, naikkan AGP, Kotlin, dan Compose BOM **bersama-sama** dan uji ulang. |
| `Unresolved reference` / `No parameter with name …` di file `ui/` | Lihat daftar di bagian 4 nomor 2. |
| Error lint di `backup_rules.xml` / `data_extraction_rules.xml` | Sesuaikan aturan menurut pesan lint; tujuannya: cloud backup mati, device transfer hidup. |
| App rilis crash, debug normal | R8. Matikan `isMinifyEnabled` / `isShrinkResources` dulu, lalu cari aturan keep yang kurang. |
