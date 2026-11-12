# Contekan Play Console — Odolog

Jawaban siap salin untuk formulir Play Console. Semua jawaban di bawah sesuai dengan perilaku
kode di repo ini (versi 1.0.0). **Kalau fitur app berubah, jawaban ini harus ditinjau ulang.**

> Nama menu dan bunyi pertanyaan di Play Console sering berubah. Saya tidak bisa membuka Play
> Console dari sini, jadi teks pertanyaannya mungkin sedikit berbeda dari yang tertulis.
> Pegang prinsip jawabannya, bukan kata per kata.

## 1. Identitas app

| Kolom | Isi |
|---|---|
| Nama app | Odolog |
| Package name (applicationId) | `com.personalizeprolabs.odolog` — **permanen setelah upload pertama** |
| Nama developer | PersonalizePro Labs |
| Bahasa default | English (United States) – en-US |
| App atau game | App |
| Gratis atau berbayar | Gratis (app gratis tidak bisa diubah jadi berbayar; "Pro sekali bayar" nanti dibuat sebagai produk dalam-app sekali beli) |
| Kategori | Auto & Vehicles (Otomotif) |
| Email kontak | `personalizeprolabs@gmail.com` (wajib, tampil publik di halaman app; sama dengan yang tertulis di blog dan di kebijakan privasi) |
| Situs web | `https://personalizeprolabs.blogspot.com` (opsional) |
| Kebijakan privasi | `https://personalizeprolabs.blogspot.com/p/odolog-privacy-policy.html` (lihat bagian 3) |

Negara/wilayah rilis yang diminta: Inggris Raya, Jerman, Amerika Serikat, Kanada, Australia.
(Opsional karena bahasanya sama: Austria, Swiss, Irlandia, Selandia Baru.)

## 2. Listing toko

Salin dari folder `play-store/listing/<bahasa>/`:

| File | Kolom di Play Console | Batas |
|---|---|---|
| `title.txt` | App name | 30 karakter |
| `short_description.txt` | Short description | 80 karakter |
| `full_description.txt` | Full description | 4000 karakter |
| `release_notes.txt` | Release notes ("What's new") saat membuat rilis | 500 karakter |

- `en-US` (bahasa default) dan `de-DE` = yang Anda minta.
- `en-GB` = tambahan untuk pasar Inggris (ejaan Inggris, menyebut MOT, screenshot dengan £, mil,
  liter dan mpg UK). Tambahkan lewat *Manage translations → English (United Kingdom)*. Tanpa ini,
  pengguna di Inggris melihat teks dan screenshot en-US (dolar, galon AS).
- Pengguna di Kanada dan Australia melihat listing en-US (bahasa default).
- Panjang semua teks sudah dicek otomatis: `python tools/check_project.py`.

Grafik:

| File | Kolom | Syarat Play |
|---|---|---|
| `play-store/graphics/icon-512.png` | App icon | 512×512 PNG |
| `play-store/graphics/feature-graphic-1024x500.png` | Feature graphic | 1024×500 PNG/JPG |
| `play-store/screenshots/<bahasa>/01…07-*.png` | Phone screenshots | 2–8 gambar, sisi 320–3840 px, rasio 16:9 atau 9:16 |

Icon dan feature graphic sama untuk semua bahasa. Screenshot ada 7 per bahasa (1080×1920),
unggah sesuai urutan nomor file:

| No | Layar | Judul en-US |
|---|---|---|
| 01 | Overview | Know what your car really costs |
| 02 | Statistics (grafik) | Fuel economy and cost per mile |
| 03 | Log | Fill-ups and service in one log book |
| 04 | Reminders | Reminders for service and inspection |
| 05 | Overview mobil listrik | Made for electric cars too |
| 06 | Formulir pengisian baru | Add a fill-up in seconds |
| 07 | Settings → Privacy | No account. No ads. Data stays on your phone. |

Semua screenshot adalah tangkapan layar asli dari app (data contoh fiktif); yang ditambahkan hanya
bingkai HP, latar, dan teks judul. Screenshot tablet 7"/10" opsional dan tidak dibuat.

Aturan kejujuran yang sudah diterapkan di teks listing (jangan dilanggar saat mengedit):

- tidak menyebut nama app lain; tidak ada klaim "terbaik", "#1", "nomor satu";
- kata kunci hanya muncul dalam kalimat wajar, tidak ditumpuk;
- ada kalimat penegasan bahwa app ini independen dan tidak terkait lembaga pemerintah / badan uji;
- kata **"TÜV" sengaja tidak dipakai** (merek terdaftar). Teks Jerman memakai "Hauptuntersuchung (HU)".
  "MOT" dipakai sebagai nama umum uji kendaraan di Inggris;
- semua klaim cocok dengan app: tanpa akun, tanpa iklan, tanpa langganan, tanpa izin internet;
- rencana fitur ditulis sebagai "planned / geplant", bukan janji tanggal.

## 3. App content (Konten aplikasi)

### Privacy policy
`https://personalizeprolabs.blogspot.com/p/odolog-privacy-policy.html`

Halaman ini harus dibuat dulu di Blogger (blog yang sama dengan app pertama):

1. Blogger → **Halaman** (Pages) → **Halaman baru**. Pakai *Halaman*, bukan *Postingan*:
   alamat halaman berbentuk `/p/judul.html`, alamat postingan memuat tahun dan bulan.
2. Judul harus persis: **`Odolog Privacy Policy`** — Blogger membentuk alamat dari judul saat
   pertama kali dipublikasikan.
3. Ganti editor ke **Tampilan HTML** (ikon pensil / `< >`), lalu tempel isi di antara dua penanda
   `BLOGGER: COPY FROM HERE` dan `BLOGGER: COPY UNTIL HERE` di `docs/privacy-policy.html`.
   Potongan siap tempel juga ada di `siap-upload/4-kebijakan-privasi/tempel-di-blogger.html`.
4. **Publikasikan**, lalu buka alamat di atas dan pastikan halamannya tampil (Inggris + Jerman).

**Status 12 November 2026: halaman sudah dipublikasikan dan alamatnya persis seperti di atas.**
Langkah di atas tetap berguna kalau isi halaman perlu diperbarui (edit halaman yang sama; alamatnya
tidak berubah).

Alamat itu tertanam di dalam app (Settings → Privacy → Full policy). Kalau suatu saat alamatnya
berubah, ubah `privacy_policy_url` di `app/src/main/res/values/config.xml`, naikkan `versionCode`,
build ulang AAB, dan pakai alamat baru itu di Play Console.

Halaman memuat nama developer, email `personalizeprolabs@gmail.com`, dan tautan ke halaman
"PersonalizePro Labs" di blog untuk alamat lengkap — sama seperti kebijakan privasi app pertama.

### App access (Akses aplikasi)
**All functionality is available without special access** — tidak ada login.

### Ads (Iklan)
**No, my app does not contain ads.**

### Content rating (Rating konten) — kuesioner IARC
- Email: `personalizeprolabs@gmail.com`.
- Kategori: **All other app types** (Utility, Productivity, Communication, or Other).
- Semua pertanyaan konten dijawab **No**:

| Pertanyaan (inti) | Jawaban |
|---|---|
| Kekerasan, darah, hal menakutkan | No |
| Konten seksual / ketelanjangan | No |
| Bahasa kasar | No |
| Obat terlarang, alkohol, tembakau | No |
| Perjudian (nyata maupun simulasi) | No |
| Pengguna bisa berinteraksi / bertukar konten dengan pengguna lain | No |
| Membagikan lokasi pengguna ke pengguna lain | No |
| Pengguna bisa membeli barang digital | No (v1 tidak ada pembelian) |
| Browser web / mesin pencari | No |
| App berita atau pendidikan | No |

Hasil yang diharapkan: **IARC 3+ / ESRB Everyone / PEGI 3 / USK 0**.
Saat fitur Pro (pembelian dalam app) ditambahkan nanti, kuesioner ini harus diisi ulang.

### Target audience and content (Target audiens)
- Kelompok usia: **18 and over** saja (app untuk pemilik kendaraan; menghindari kewajiban kebijakan Families).
- "Could your store listing unintentionally appeal to children?" → **No**.

### Data safety (Keamanan data)

| Pertanyaan | Jawaban |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |

Dengan jawaban **No**, formulir selesai dan halaman app menampilkan "No data collected" dan
"No data shared with third parties". Pertanyaan lanjutan (enkripsi saat transit, permintaan
penghapusan data) tidak muncul.

Dasar jawabannya (simpan sebagai pegangan kalau ditanya reviewer):

- Menurut definisi Play, "collect" = data dikirim **keluar dari perangkat**. Odolog menyimpan
  semuanya di penyimpanan privat app; tidak ada yang dikirim ke developer atau pihak ketiga.
- App **tidak punya izin `INTERNET`**, tanpa SDK iklan/analitik/crash, tanpa akun.
- Ekspor CSV dilakukan pengguna sendiri ke lokasi yang ia pilih lewat pemilih file sistem —
  bukan pengumpulan data oleh developer.
- Cloud backup Android dimatikan untuk app ini (`res/xml/data_extraction_rules.xml`,
  `res/xml/backup_rules.xml`); yang diizinkan hanya transfer langsung HP-ke-HP.

Pertanyaan terkait lain:

| Formulir | Jawaban |
|---|---|
| Account creation / account deletion | App tidak punya fitur pembuatan akun |
| Advertising ID — "Does your app use advertising ID?" | **No** |
| Government apps | No |
| Financial features | **My app doesn't provide any financial features** (pencatat biaya pribadi bukan layanan keuangan) |
| Health apps | App tidak punya fitur kesehatan |
| News apps | No |
| COVID-19 contact tracing / status | No |

### Izin (permissions)
Manifest app hanya meminta:

| Izin | Untuk apa | Perlu deklarasi khusus? |
|---|---|---|
| `POST_NOTIFICATIONS` | menampilkan notifikasi pengingat (Android 13+ bertanya ke pengguna) | Tidak |
| `RECEIVE_BOOT_COMPLETED` | menjadwalkan ulang pengecekan pengingat setelah HP restart | Tidak |

Tidak ada: lokasi, exact alarm (`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`), kontak, kamera,
penyimpanan, foreground service, internet. Jadi **tidak ada formulir deklarasi izin sensitif**
yang perlu diisi.

Setelah upload bundle, periksa di *App bundle explorer → Permissions* bahwa daftarnya memang
hanya itu (AndroidX menambahkan satu izin internal bernama
`…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; itu normal dan bukan izin sensitif).
Kalau ternyata muncul `INTERNET`, **jangan rilis** — berarti ada library yang menambahkannya,
dan klaim "tanpa internet" di listing serta kebijakan privasi jadi salah.

## 4. Hal lain yang biasa ditanyakan saat rilis

- **Play App Signing**: terima (default). Kunci di `signing/odolog-upload.jks` adalah *upload key*;
  AAB di `siap-upload/1-aab/` sudah ditandatangani dengan kunci itu. Cadangkan file keystore dan
  `keystore.properties` (lihat `docs/CATATAN-PC-BUILD.md` bagian 4).
- **versionCode**: AAB sekarang memakai `versionCode 2` (versionName tetap 1.0.0). Kode 1 pernah
  diupload lalu dibatalkan, dan Play Console menolak kode yang pernah dipakai walaupun rilisnya
  dibuang. Kalau sebuah upload dibatalkan lagi setelah AAB-nya terunggah, naikkan kodenya lagi di
  `app/build.gradle.kts`, build ulang, lalu jalankan `python tools/collect_release.py`.
- **Akun developer pribadi baru** (dibuat setelah 13 November 2023): Google mewajibkan *closed
  testing* dengan minimal 12 penguji selama 14 hari sebelum boleh rilis produksi. Cek status akun
  Anda di Play Console; kalau berlaku, mulai dari track *Closed testing*.
- **Status trader (UE)**: untuk distribusi di Uni Eropa, Play meminta pernyataan trader /
  non-trader di pengaturan akun. Itu keputusan Anda sebagai developer; untuk app yang kelak
  menjual fitur Pro, biasanya "trader".
- **Target API**: app menargetkan API 36 (`targetSdk = 36`), sesuai permintaan.
- **Ukuran halaman memori 16 KB**: app tidak punya kode native sendiri. Satu-satunya library
  native berasal dari Jetpack Compose (`libandroidx.graphics.path.so`); di dalam AAB rilis
  sudah diperiksa: semua segmen LOAD-nya selaras 16 KB (0x4000) untuk keempat ABI. Periksa lagi
  peringatan Play Console setelah upload.

## 5. Daftar periksa sebelum menekan "Send for review"

- [x] Halaman kebijakan privasi sudah dipublikasikan di Blogger dan bisa dibuka di alamat di atas
      (dicek 12 November 2026)
- [ ] Alamat kebijakan privasi di Play Console **sama persis** dengan `privacy_policy_url` di
      `app/src/main/res/values/config.xml`
- [ ] APK rilis sudah dicoba di HP sungguhan (sejauh ini hanya diuji di emulator Android 16) —
      lihat `docs/CATATAN-PC-BUILD.md` bagian 8 dan 9
- [ ] Screenshot diunggah per bahasa: en-US, en-GB, de-DE (folder `play-store/screenshots/`)
- [ ] Daftar izin di App bundle explorer sesuai tabel di atas
- [ ] Laporan pra-peluncuran (pre-launch report) Play Console sudah dibaca, tidak ada crash
- [ ] `python tools/check_project.py` → 0 error
