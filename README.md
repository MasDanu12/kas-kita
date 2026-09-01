# Kas Kita — Redesign v3

Aplikasi pengelolaan keuangan organisasi (kas kelas/RT/komunitas) berbasis Cloudflare Workers + D1 + R2, dengan frontend PWA vanilla JS (tanpa framework berat).

## Struktur folder

```
kas-kita-redesign/
├── worker.js         # Backend API lengkap (auth, organisasi, anggota, transaksi, iuran, laporan, profil)
├── schema.sql        # Skema database D1
├── wrangler.toml     # Konfigurasi deploy Cloudflare
├── public/
│   ├── index.html    # Frontend PWA (semua 6 halaman dalam 1 file, SPA hash-routing)
│   ├── manifest.json # Manifest PWA
│   └── sw.js          # Service worker (cache app shell, bukan cache API)
```

## Langkah setup (urutan penting)

### 1. Buat resource Cloudflare
```bash
wrangler d1 create kas-kita-db
# salin database_id yang muncul, tempel ke wrangler.toml
```

### 2. Jalankan migrasi skema
```bash
wrangler d1 execute kas-kita-db --file=./schema.sql --remote
```

### 3. Set secrets (jangan pernah ditulis di wrangler.toml)
```bash
wrangler secret put JWT_SECRET
# isi dengan string acak panjang, contoh: openssl rand -hex 32

wrangler secret put MAILCHANNELS_API_KEY
# isi dengan API key dari akun MailChannels Anda
```

### 4. Setup DNS untuk MailChannels (supaya email reset password tidak masuk spam)
Tambahkan record berikut di DNS domain pengirim (`MAIL_FROM` di wrangler.toml):
- **SPF (TXT)**: `v=spf1 include:relay.mailchannels.net ~all`
- **Domain Lockdown (TXT)** di `_mailchannels.namadomain.com`: `v=mc1 cfid=namaworkersanda.workers.dev`

Ikuti dokumentasi resmi MailChannels untuk detail terbaru — format record bisa berubah sewaktu-waktu.

### 5. Edit `wrangler.toml`
- Isi `database_id` dari langkah 1
- Isi `APP_URL` dengan domain worker Anda (dipakai untuk link reset password & link foto R2)
- Isi `MAIL_FROM` dengan email pengirim yang sudah diverifikasi

### 6. Deploy
```bash
wrangler deploy
```

## Login Google (opsional)
Endpoint `/api/auth/google` menerima `id_token` dari Google Sign-In JS SDK di sisi frontend. Frontend saat ini **belum** menyertakan tombol Login Google fungsional (perlu Client ID dari Google Cloud Console) — perlu ditambahkan manual di `public/index.html` bagian `renderLogin()` kalau ingin diaktifkan.

## Catatan penting / batasan yang perlu diketahui
- **Foto profil**: tidak pakai upload foto asli — memakai avatar inisial nama (huruf pertama) agar tidak perlu layanan penyimpanan file berbayar/tambahan (Cloudflare R2 sengaja tidak dipakai sesuai keputusan).
- **Struk transaksi**: digenerate di sisi browser (Canvas → PNG), bukan di server. Tombol "bagikan ke WhatsApp/Telegram" akan mengunduh PNG dulu lalu membuka aplikasi tujuan (keterbatasan teknis: browser tidak bisa mengirim file langsung ke WhatsApp/Telegram lewat URL scheme).
- **Laporan PDF**: dibuat lewat `window.print()` di tab baru dengan layout khusus cetak, bukan generate PDF asli di server. Pengguna tinggal pilih "Simpan sebagai PDF" di dialog print browser.
- **Ikon navigasi**: memakai emoji (🏠👥📄👤) sebagai pengganti ikon custom, supaya tidak perlu dependency font ikon eksternal. Bisa diganti ke SVG/ikon custom kapan saja tanpa mengubah logika.
- **Login Google**: backend sudah siap, tombol UI belum disambungkan ke Google SDK asli (lihat bagian di atas).
- **Notifikasi otomatis** (`generateNotifikasiOtomatis` di worker.js) belum dipanggil terjadwal — perlu ditambahkan **Cron Trigger** di `wrangler.toml` kalau mau jalan otomatis harian, contoh:
  ```toml
  [triggers]
  crons = ["0 1 * * *"]
  ```
  lalu tambahkan handler `scheduled()` di `worker.js` yang memanggil `generateNotifikasiOtomatis` untuk tiap organisasi.
