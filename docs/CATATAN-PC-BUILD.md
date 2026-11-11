# Catatan build — Odolog

Source code ini awalnya ditulis tanpa pernah di-build. Sejak **11 November 2026** proyek ini
sudah di-build dengan Gradle, dipasang dan diuji di emulator, dan paket rilisnya sudah dibuat.
Dokumen ini menjelaskan cara mengulanginya, apa yang sudah terbukti jalan, dan apa yang
**belum** terverifikasi.

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

Semua nomor versi ada di satu file: `gradle/libs.versions.toml`.

Struktur:

```
app/src/main/java/com/personalizeprolabs/odolog/
  core/        logika murni (tanpa Android): model, satuan, statistik, CSV, pengingat, format
  data/        SQLite (AppDatabase), Repository, SettingsStore (SharedPreferences)
  reminders/   alarm harian tidak-eksak + notifikasi
  ui/          semua layar Compose
app/src/test/  unit test untuk paket core (JUnit 4)
tools/         script Python: pemeriksaan, tes di perangkat, screenshot, gambar, paket upload
play-store/    teks listing, grafik, screenshot
docs/          kebijakan privasi, contekan Play Console, data contoh, catatan ini
```

## 2. Yang dibutuhkan

- **Android Studio** yang mendukung AGP 8.13 (Narwhal 3 Feature Drop | 2025.1.3 atau lebih baru).
- **JDK 17 atau 21** untuk menjalankan Gradle. Pakai JDK bawaan Android Studio (JBR 21):
  untuk baris perintah, set `JAVA_HOME` ke folder `jbr` di dalam Android Studio.
  Gradle 8.13 **tidak** jalan di Java 24 ke atas ("Unsupported class file major version").
- **Android SDK Platform 36** (SDK Manager) dan `local.properties` berisi `sdk.dir=…`
  (dibuat otomatis saat proyek dibuka di Android Studio; file ini di-ignore git).
- Koneksi internet untuk build pertama (Gradle mengunduh dependensi).
- Python 3.10+ untuk script di `tools/`; Pillow hanya untuk script gambar:
  `python -m venv .venv` lalu `.venv/Scripts/python -m pip install -r tools/requirements.txt`.

## 3. Build

Di Git Bash (Windows); di PowerShell pakai `.\gradlew` dan `$env:JAVA_HOME = "…"`.

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"

python tools/check_project.py                 # pemeriksaan statis, harus "0 error(s)"
./gradlew testDebugUnitTest                   # 56 unit test logika inti
./gradlew lintRelease                         # laporan: app/build/reports/lint-results-release.html
./gradlew assembleDebug                       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease bundleRelease       # APK + AAB rilis (lihat bagian 4)
```

Kalau `clean` gagal karena file terkunci, hentikan daemon dulu: `./gradlew --stop`.

## 4. Build rilis dan upload key

Build rilis membaca `keystore.properties` di root proyek (contoh: `keystore.properties.example`).

| File | Isi |
|---|---|
| `signing/odolog-upload.jks` | upload keystore (PKCS12, alias `upload`, RSA 2048, berlaku sampai 2054) |
| `keystore.properties` | lokasi keystore, alias, dan sandinya |

Kedua file itu **hanya ada di PC build**, sudah ada di `.gitignore`, dan **tidak boleh pernah
di-commit — repo ini publik**. Cadangkan keduanya di tempat aman di luar PC (dua tempat lebih
baik). Kalau hilang, update berikutnya tidak bisa ditandatangani dengan kunci yang sama; dengan
Play App Signing kunci upload masih bisa direset lewat dukungan Google, tetapi itu makan waktu.

Sidik jari sertifikat upload (informasi publik, tercantum juga di setiap APK):

```
SHA-256  1A:FD:D2:6A:4F:49:AF:6B:FA:57:E5:89:FA:EB:39:F5:58:75:47:96:E3:4A:33:D4:9E:00:45:32:21:5E:06:D1
SHA-1    37:07:A6:26:EB:CE:02:68:21:ED:F3:E5:B8:60:1A:85:2E:E7:01:CA
```

Membuat keystore baru (hanya kalau belum punya — **jangan** menimpa yang sudah dipakai upload):

```bash
keytool -genkeypair -v -keystore signing/odolog-upload.jks -storetype PKCS12 -alias upload \
        -keyalg RSA -keysize 2048 -validity 10000
```

Hasil build:

- `app/build/outputs/bundle/release/app-release.aab` — yang diupload ke Play Console
- `app/build/outputs/apk/release/app-release.apk` — untuk dicoba di HP (`adb install -r …`)

Tanpa `keystore.properties`, build rilis tetap jalan tetapi hasilnya tidak ditandatangani.
Untuk rilis berikutnya naikkan `versionCode` (wajib, +1) dan `versionName`.

## 5. Tes otomatis di perangkat

```bash
python tools/device_test.py --apk app/build/outputs/apk/release/app-release.apk
```

Butuh HP dengan USB debugging atau emulator (`adb devices` harus menampilkan satu perangkat),
bahasa perangkat Inggris (AS). **Data app di perangkat itu dihapus.** Script memasang APK, lalu
mengoperasikan app lewat `adb` seperti pengguna dan memeriksa hasilnya:

- layar awal dan satuan default; formulir kosong ditolak;
- tiga pengisian penuh → 32,7 mpg dan $0,157/mil (dihitung manual sebagai pembanding);
- biaya servis, filter di Log, edit, hapus;
- pengingat: validasi, pemilih tanggal, dialog izin notifikasi, "Done" pada pengingat berulang;
- Statistics dan grafik; ganti satuan ke km / L/100 km;
- ekspor CSV, hapus semua data, impor, impor kedua tanpa duplikat;
- rotasi layar saat mengisi formulir;
- notifikasi pengingat: muncul, tidak dobel, ketukan membuka tab Reminders;
- tidak ada crash di logcat.

Emulator yang dipakai di PC ini: AVD `Odolog_Test` (1080×2400, Android 16, image Google Play),
datanya di folder `.avd/` (di-ignore git).

## 6. Screenshot Play Store

Semua screenshot adalah tangkapan layar asli dari app yang berjalan; hanya bingkai, latar, dan
teks judul yang ditambahkan.

```bash
python tools/make_demo_data.py        # data contoh bertanggal segar -> docs/demo-data/*.csv
python tools/take_screenshots.py      # tangkapan asli  -> play-store/screenshots/raw/<bahasa>/
python tools/frame_screenshots.py     # versi Play Store -> play-store/screenshots/<bahasa>/  (1080×1920)
```

- `take_screenshots.py` menghapus data app, mengganti bahasa app (en-US, en-GB, de-DE),
  mengimpor data contoh, lalu memotret 7 layar per bahasa. Butuh Android 13+.
- Teks judul per gambar ada di `play-store/screenshots/captions.json`.
- Latar `play-store/graphics/screenshot-background.png` dibuat sekali dengan
  `python tools/generate_images.py --only screenshot_bg` (4 kredit). Tanpa file itu,
  `frame_screenshots.py` memakai gradasi polos, jadi tidak wajib punya API key.

## 7. Folder siap upload

```bash
python tools/collect_release.py       # -> siap-upload/  (di-ignore git)
```

Mengumpulkan AAB, APK uji, teks listing, icon, feature graphic, screenshot, kebijakan privasi
(termasuk potongan HTML untuk ditempel di Blogger), dan contekan formulir, plus `BACA-DULU.md`.
Script menolak jalan kalau AAB belum ditandatangani atau lebih tua daripada source code.

## 8. Status verifikasi

### Sudah terbukti (11 November 2026, PC build + emulator Android 16)

| Apa | Cara | Hasil |
|---|---|---|
| Kompilasi | `./gradlew assembleDebug assembleRelease bundleRelease` | berhasil, tanpa error |
| Logika inti (`core/`) | `./gradlew testDebugUnitTest` | 56 test lulus |
| Lint | `./gradlew lintRelease` | "No issues found" (yang dimatikan hanya pengingat versi dependensi, lihat `app/build.gradle.kts`) |
| Build rilis dengan R8 (minify + shrink) | `tools/device_test.py --apk …/app-release.apk` | semua pemeriksaan lulus |
| Tampilan lain | dilihat langsung di emulator | mode gelap, bahasa Jerman, huruf sistem terbesar, rotasi: baik |
| Notifikasi | pengingat jatuh tempo → notifikasi; ketuk → tab Reminders; alarm harian terjadwal 09:00 | baik |
| Izin di APK dan AAB | `aapt2 dump badging` + isi manifest di dalam AAB | hanya `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, dan izin internal AndroidX `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; **tidak ada `INTERNET`** |
| Tanda tangan AAB | `jarsigner -verify` | "jar verified", ditandatangani upload key |
| Data contoh | diimpor di app; angka statistik sama dengan hitungan di luar app | cocok |
| Teks listing, grafik, screenshot, kebijakan privasi | `python tools/check_project.py` | 0 error |

Catatan: di manifest ada tulisan `android.permission.DUMP`. Itu bukan izin yang diminta app,
melainkan pelindung receiver bawaan AndroidX (`ProfileInstallReceiver`); normal untuk app Compose.

### BELUM terverifikasi

1. **HP sungguhan.** Semua tes dijalankan di emulator. Coba APK rilis di HP sendiri sebelum
   rilis produksi (`adb install -r siap-upload/1-aab/…apk`), terutama di HP dengan penghemat
   baterai agresif (Xiaomi, Oppo, Samsung): pengingat harian bisa tertunda di sana.
2. **Android versi lama.** Emulator yang dipakai Android 16. minSdk app 26 (Android 8.0);
   lint tidak menemukan pemakaian API yang terlalu baru, tetapi app belum pernah dijalankan di
   Android 8–15.
3. **Alarm sungguhan dan restart.** Notifikasi diuji dengan memicu pengecekan secara langsung.
   Menunggu alarm 09:00 yang asli, mode Doze, dan restart HP belum diuji.
4. **Aturan backup** (cloud backup mati, transfer HP-ke-HP hidup) ditulis menurut dokumentasi;
   belum diuji dengan `adb shell bmgr` atau pindah HP sungguhan.
5. **TalkBack / aksesibilitas** belum diuji manual (label tombol dan urutan fokus).
6. **Play Console.** Formulir di `docs/CONTEKAN-PLAY-CONSOLE.md` ditulis tanpa membuka Play
   Console; bunyi pertanyaan bisa berbeda. Laporan pra-peluncuran (pre-launch report) Play akan
   menguji app di beberapa perangkat sungguhan setelah AAB diupload — bacalah hasilnya.
7. **Halaman kebijakan privasi** baru ada sebagai file; alamat di dalam app baru benar setelah
   halaman Blogger dipublikasikan dengan judul yang tepat (lihat contekan, bagian 3).

## 9. Daftar tes manual (yang belum dicakup tes otomatis)

- [ ] Pasang APK rilis di HP sungguhan; buka, isi beberapa data, tutup, buka lagi
- [ ] Ganti jenis penggerak ke *Electric* → menu tambah hanya menawarkan "Charge" + "Service or expense"
- [ ] Ketik angka dengan koma dan titik (`45,5` / `45.5`) → terbaca benar sesuai bahasa HP
- [ ] Pengisian sebagian (hapus centang "Full tank") dijumlahkan ke interval berikutnya
- [ ] Odometer lebih kecil dari entri sebelumnya → muncul peringatan, tetap bisa disimpan
- [ ] Buat pengingat jatuh tempo besok, biarkan HP semalam → notifikasi muncul sekitar pukul 09:00
- [ ] Restart HP → pengingat tetap bekerja keesokan harinya
- [ ] Matikan notifikasi app di pengaturan sistem → kartu peringatan muncul di tab Reminders
- [ ] Export CSV → file bisa dibuka di Excel/Sheets; huruf umlaut benar
- [ ] Mode pesawat: semua fungsi tetap jalan
- [ ] Privacy → "Full policy" membuka halaman kebijakan privasi di Blogger
- [ ] Icon launcher terlihat benar (lingkaran, squircle, dan ikon bertema di Android 13+)

## 10. Script gambar (icon, feature graphic, latar screenshot)

Hasilnya sudah ada di repo, jadi **tidak perlu dijalankan lagi**:

- `play-store/graphics/icon-512.png`, `feature-graphic-1024x500.png`, `screenshot-background.png`
- `play-store/graphics/raw/` — semua hasil asli dari API + `jobs-state.json`
- `app/src/main/res/mipmap-*/ic_launcher_foreground.png`, `ic_launcher_monochrome.png`,
  `res/values/ic_launcher_background.xml`

Kalau ingin varian baru:

```bash
# salin .env.example menjadi .env dan isi GEMINIGEN_API_KEY  (.env tidak pernah di-commit)
.venv/Scripts/python tools/generate_images.py                # tidak membuat ulang yang sudah ada
.venv/Scripts/python tools/generate_images.py --new icon     # satu varian icon tambahan (pakai kredit)
.venv/Scripts/python tools/generate_images.py --select icon icon-01-8ecb1a2a.png   # kembali ke varian lama
.venv/Scripts/python tools/generate_images.py --process-only # hanya membuat ulang file turunan
```

Cara kerja tanpa webhook: script mengirim permintaan, menerima `uuid`, lalu memeriksa
`GET /uapi/v1/history/{uuid}` berkala sampai selesai dan mengunduh hasilnya. Model yang dipakai:
GPT Image 2, mode `low`, resolusi `4K` (4 kredit per gambar). Kalau satu permintaan gagal di
sisi server, jalankan perintah yang sama sekali lagi; percobaan gagal tercatat di `jobs-state.json`.

## 11. Keputusan desain yang perlu diketahui

- **Tanpa izin INTERNET.** Tautan kebijakan privasi dibuka lewat browser HP (intent), jadi app
  sendiri tetap tidak butuh internet.
- **Cloud backup Android dimatikan**, transfer langsung HP-ke-HP diizinkan
  (`res/xml/data_extraction_rules.xml`). Ini supaya klaim "data hanya di HP" benar secara harfiah.
  Jalur resmi ganti HP di v1 adalah ekspor/impor CSV.
- **Pengingat memakai alarm tidak-eksak** (`AlarmManager.setAndAllowWhileIdle`), dicek sekali
  sehari sekitar pukul 09:00; tidak butuh izin exact alarm. Android boleh menggesernya beberapa jam.
  Dijadwalkan ulang saat app dibuka, setelah restart HP, dan setelah update app.
- **Notifikasi pengingat dikelompokkan sendiri** (grup + ringkasan milik app). Tanpa itu Android
  menggabungkannya otomatis dan ketukan pada gabungan itu membuka Overview, bukan tab Reminders.
- **Satu kendaraan** di v1. Tabel database sudah punya kolom `vehicle_id`, jadi multi-kendaraan
  bisa ditambahkan tanpa membongkar tabel.
- **Format CSV** didokumentasikan di `core/Backup.kt`. Kode enum (`fuel`, `charge`, `service`, …)
  disimpan di database dan CSV pengguna: jangan diubah, hanya boleh ditambah.
- **Tanpa library navigasi, DI, Room, DataStore, WorkManager, atau ikon**: sengaja, supaya
  dependensi minimal. Ikon adalah vektor Material (Apache 2.0) di `res/drawable`.
- **Bahasa**: default Inggris (ejaan AS), Jerman lengkap, plus penyesuaian kecil untuk en-GB,
  en-AU, en-CA. Menambah string baru: tambahkan di `values/strings.xml` **dan**
  `values-de/strings.xml`, lalu jalankan `python tools/check_project.py`.

## 12. Kalau build gagal

| Gejala | Kemungkinan penyebab / solusi |
|---|---|
| `Unsupported class file major version …` | Gradle dijalankan dengan JDK terlalu baru. Pakai JDK 17/21 (JBR Android Studio). |
| `SDK location not found` | Buka proyek sekali di Android Studio, atau buat `local.properties` berisi `sdk.dir=…`. |
| `Failed to install … licences have not been accepted` | Setujui lisensi di SDK Manager Android Studio. |
| `Could not resolve …` | Tidak ada internet, atau proxy. |
| `clean` gagal, file terkunci | `./gradlew --stop`, lalu ulangi. |
| `AAPT: error: resource … not found` setelah memindah file resource | `./gradlew --stop` lalu `./gradlew clean assembleDebug`. |
| Android Studio menawarkan upgrade AGP/Kotlin | Boleh ditolak. Kalau mau upgrade, naikkan AGP, Kotlin, dan Compose BOM **bersama-sama** dan uji ulang. |
| App rilis crash, debug normal | R8. Matikan `isMinifyEnabled` / `isShrinkResources` dulu, lalu cari aturan keep yang kurang. |
