# Panduan Setup & Deploy - Kas Kita

Semua langkah ini bisa dilakukan lewat **Cloudflare Dashboard** di HP (browser), tanpa command line, sama seperti waktu kamu setup Dompetku.

## Struktur File
```
kas-kita/
├── worker.js          <- Cloudflare Worker (backend + serve static)
├── schema.sql          <- Skema database D1
├── wrangler.toml        <- Konfigurasi deploy
└── public/
    ├── index.html       <- Frontend PWA
    ├── manifest.json
    ├── sw.js
    └── icon.png
```
Upload struktur folder ini ke repo GitHub kamu (bikin repo baru, misal `kas-kita`).

## 1. Buat Database D1
1. Buka **Cloudflare Dashboard** → **Workers & Pages** → **D1** (di menu Storage & Databases)
2. Klik **Create Database**, beri nama `kas-kita-db`
3. Setelah dibuat, buka tab **Console** di database itu
4. Copy seluruh isi file `schema.sql`, paste ke Console, lalu jalankan (Execute) — ini akan membuat semua tabel
5. Catat **Database ID** yang muncul di halaman detail database itu

## 2. Sesuaikan `wrangler.toml`
Buka file `wrangler.toml`, ganti baris:
```
database_id = "GANTI_DENGAN_DATABASE_ID_DARI_DASHBOARD"
```
dengan Database ID dari langkah 1.

## 3. Deploy Worker lewat GitHub
1. Push semua file (worker.js, schema.sql, wrangler.toml, folder public/) ke repo GitHub
2. Di Dashboard → **Workers & Pages** → **Create** → **Workers** → pilih **Connect to Git**
3. Pilih repo `kas-kita` kamu
4. Build command: **kosongkan saja** (tidak perlu build, ini vanilla JS)
5. Deploy command otomatis akan pakai `wrangler.toml` yang sudah kamu commit

## 4. Hubungkan Database D1 ke Worker
Jika binding D1 belum otomatis terbaca dari `wrangler.toml` saat connect-to-git:
1. Buka Worker kamu → **Settings** → **Bindings**
2. Tambah binding **D1 Database**: Variable name = `DB`, pilih database `kas-kita-db`

## 5. Set Environment Variable (Secret)
1. Di Worker → **Settings** → **Variables and Secrets**
2. Tambah secret baru: nama `JWT_SECRET`, isi dengan teks acak yang panjang dan rahasia (contoh: gabungan huruf-angka minimal 32 karakter). Ini dipakai untuk mengamankan token login.
3. Simpan (Encrypt)

## 6. Aktifkan Static Assets
1. Pastikan binding **Assets** di Worker mengarah ke folder `public/` (biasanya otomatis terbaca dari `wrangler.toml` saat connect-to-git)
2. Jika tidak otomatis: Worker → **Settings** → **Bindings** → tambah binding **Assets**, arahkan ke folder `public`

## 7. Selesai — Coba Akses
Buka URL worker kamu (misal `https://kas-kita.<namamu>.workers.dev`), akan muncul halaman daftar/login.

### Cara pakai untuk organisasi:
1. Daftar akun (email + password)
2. Pilih **"Buat Organisasi Baru"**, isi nama lembaga → sistem kasih **kode undangan 8 karakter**
3. Bagikan kode itu ke admin/bendahara lain → mereka pilih **"Gabung"** dan masukkan kode
4. Semua admin yang gabung punya akses penuh yang sama ke data organisasi itu

### Install ke HP (PWA):
Buka di Chrome Android → menu (⋮) → **"Add to Home screen" / "Install app"**

---

## Catatan Teknis
- **Struk & Laporan PNG**: dibuat manual pakai Canvas API (tanpa library eksternal), langsung download ke galeri/file HP
- **Struk & Laporan PDF**: memakai `window.print()` browser → pilih **"Save as PDF"** di dialog print (tidak butuh library PDF eksternal yang berat)
- **Grafik**: donat pakai SVG manual, bar chart tren tahunan pakai HTML/CSS manual — sama seperti prinsip di Dompetku, tanpa dependency
- Semua tetap gratis di Cloudflare Free Tier (Workers, D1, dan Assets semua ada di free tier)
