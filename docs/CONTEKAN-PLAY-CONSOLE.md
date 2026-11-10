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
| Email kontak | *(isi email Anda — wajib, tampil publik di halaman app)* |
| Situs web | opsional |
| Kebijakan privasi | `https://kantoryoga.web.id/odolog/privacy-policy.html` |

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

- `en-US` dan `de-DE` = yang Anda minta.
- `en-GB` = **bonus opsional** (ejaan Inggris, menyebut MOT). Tambahkan lewat *Manage translations →
  English (United Kingdom)*. Tanpa ini, pengguna di Inggris melihat teks en-US.
- Panjang semua teks sudah dicek otomatis: `python tools/check_project.py`.

Grafik (folder `play-store/graphics/`):

| File | Kolom | Syarat Play |
|---|---|---|
| `icon-512.png` | App icon | 512×512 PNG |
| `feature-graphic-1024x500.png` | Feature graphic | 1024×500 PNG/JPG |
| *(dibuat di PC build)* | Phone screenshots | 2–8 gambar, sisi 320–3840 px, rasio 16:9 atau 9:16 |

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
`https://kantoryoga.web.id/odolog/privacy-policy.html`
Isi dulu placeholder `[[CONTACT_EMAIL]]` dan `[[POSTAL_ADDRESS]]` di `docs/privacy-policy.html`, lalu unggah.

### App access (Akses aplikasi)
**All functionality is available without special access** — tidak ada login.

### Ads (Iklan)
**No, my app does not contain ads.**

### Content rating (Rating konten) — kuesioner IARC
- Email: email kontak Anda.
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

- **Play App Signing**: terima (default). Kunci yang Anda buat di PC build adalah *upload key*.
- **Akun developer pribadi baru** (dibuat setelah 13 November 2023): Google mewajibkan *closed
  testing* dengan minimal 12 penguji selama 14 hari sebelum boleh rilis produksi. Cek status akun
  Anda di Play Console; kalau berlaku, mulai dari track *Closed testing*.
- **Status trader (UE)**: untuk distribusi di Uni Eropa, Play meminta pernyataan trader /
  non-trader di pengaturan akun. Itu keputusan Anda sebagai developer; untuk app yang kelak
  menjual fitur Pro, biasanya "trader".
- **Target API**: app menargetkan API 36 (`targetSdk = 36`), sesuai permintaan.
- **Ukuran halaman memori 16 KB**: app tidak punya kode native sendiri. Satu-satunya library
  native berasal dari Jetpack Compose (`libandroidx.graphics.path.so`); versi 1.0.1-nya sudah
  saya periksa selaras 16 KB. Periksa lagi peringatan Play Console setelah upload.

## 5. Daftar periksa sebelum menekan "Send for review"

- [ ] Placeholder di `docs/privacy-policy.html` sudah diisi dan halamannya bisa dibuka di alamat di atas
- [ ] Alamat kebijakan privasi di Play Console **sama persis** dengan `privacy_policy_url` di
      `app/src/main/res/values/config.xml`
- [ ] Build rilis (bukan debug) sudah dicoba di HP sungguhan — lihat `docs/CATATAN-PC-BUILD.md`
- [ ] Screenshot diambil dari app asli, dalam bahasa yang sesuai listing (Inggris untuk en-US, Jerman untuk de-DE)
- [ ] Daftar izin di App bundle explorer sesuai tabel di atas
- [ ] `python tools/check_project.py` → 0 error
