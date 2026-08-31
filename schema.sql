-- ============================================================
-- KAS KITA — Skema Database D1 (Redesign v3)
-- Berbasis skema acuan lama (kas-kita-database-acuan.sql)
-- Ditambah field baru hasil keputusan redesign 6-mockup
-- ============================================================

PRAGMA foreign_keys = ON;

-- ------------------------------------------------------------
-- USERS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,                         -- UUID
  nama TEXT NOT NULL,
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT,                          -- NULL kalau login via Google
  no_hp TEXT,
  foto_url TEXT,                                -- NEW: link foto profil di R2
  tema TEXT NOT NULL DEFAULT 'terang' CHECK (tema IN ('terang','gelap')), -- NEW
  google_id TEXT UNIQUE,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ------------------------------------------------------------
-- PASSWORD RESET TOKENS (untuk lupa password via email)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_resets (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  used INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_password_resets_user ON password_resets(user_id);

-- ------------------------------------------------------------
-- ORGANIZATIONS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS organizations (
  id TEXT PRIMARY KEY,                         -- UUID
  nama TEXT NOT NULL,
  kode_id TEXT UNIQUE NOT NULL,                 -- NEW: format KITA-{SLUG}-{NNN}
  created_by TEXT NOT NULL REFERENCES users(id),
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_organizations_kode ON organizations(kode_id);

-- ------------------------------------------------------------
-- ORGANIZATION_MEMBERS (relasi user <-> organisasi, semua admin akses sama rata)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS organization_members (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  jabatan TEXT NOT NULL DEFAULT 'Anggota',      -- NEW: label tampilan saja, tidak pengaruhi akses
  joined_at TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(organization_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_org_members_org ON organization_members(organization_id);
CREATE INDEX IF NOT EXISTS idx_org_members_user ON organization_members(user_id);

-- ------------------------------------------------------------
-- AKUN (akun kas: tunai/bank/e-wallet)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS akun (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  nama TEXT NOT NULL,
  jenis TEXT NOT NULL CHECK (jenis IN ('tunai','bank','e_wallet')),
  nomor_rekening TEXT,
  saldo_awal INTEGER NOT NULL DEFAULT 0,        -- dalam rupiah (integer, hindari float)
  nonaktif INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_akun_org ON akun(organization_id);

-- ------------------------------------------------------------
-- KATEGORI (kategori transaksi pengeluaran/pemasukan lain)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS kategori (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  nama TEXT NOT NULL,
  tipe TEXT NOT NULL CHECK (tipe IN ('pemasukan','pengeluaran')),
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(organization_id, nama, tipe)
);
CREATE INDEX IF NOT EXISTS idx_kategori_org ON kategori(organization_id);

-- ------------------------------------------------------------
-- ANGGOTA
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS anggota (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  nama TEXT NOT NULL,
  no_hp TEXT,
  tanggal_bergabung TEXT NOT NULL,              -- dasar hitung mulai kewajiban iuran
  dikeluarkan_at TEXT,                          -- NEW: soft-delete, NULL = masih aktif
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_anggota_org ON anggota(organization_id);
CREATE INDEX IF NOT EXISTS idx_anggota_aktif ON anggota(organization_id, dikeluarkan_at);

-- ------------------------------------------------------------
-- IURAN_SETTINGS (satu baris per organisasi — nominal iuran wajib)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iuran_settings (
  organization_id TEXT PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
  nominal_bulanan INTEGER NOT NULL DEFAULT 0,
  tanggal_mulai_organisasi TEXT NOT NULL,       -- dasar hitung periode iuran pertama
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_by TEXT REFERENCES users(id)
);

-- ------------------------------------------------------------
-- IURAN_PEMBAYARAN (satu baris = satu transaksi pembayaran iuran)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iuran_pembayaran (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  anggota_id TEXT NOT NULL REFERENCES anggota(id),
  akun_id TEXT NOT NULL REFERENCES akun(id),
  jumlah INTEGER NOT NULL,
  metode_pembayaran TEXT NOT NULL DEFAULT 'Tunai'
    CHECK (metode_pembayaran IN ('Tunai','Transfer Bank','QRIS','E-Wallet')), -- NEW
  tanggal_pembayaran TEXT NOT NULL,
  catatan TEXT,
  dicatat_oleh TEXT NOT NULL REFERENCES users(id),
  transaksi_id TEXT REFERENCES transaksi(id),   -- link ke pencatatan kas (1:1, no duplikasi)
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_iuran_bayar_org ON iuran_pembayaran(organization_id);
CREATE INDEX IF NOT EXISTS idx_iuran_bayar_anggota ON iuran_pembayaran(anggota_id);

-- ------------------------------------------------------------
-- IURAN_ALOKASI (hasil pemecahan 1 pembayaran ke beberapa periode, FIFO)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iuran_alokasi (
  id TEXT PRIMARY KEY,
  pembayaran_id TEXT NOT NULL REFERENCES iuran_pembayaran(id) ON DELETE CASCADE,
  anggota_id TEXT NOT NULL REFERENCES anggota(id),
  periode TEXT NOT NULL,                        -- format 'YYYY-MM'
  jumlah_dialokasikan INTEGER NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('lunas','sebagian')),
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_alokasi_anggota_periode ON iuran_alokasi(anggota_id, periode);

-- ------------------------------------------------------------
-- TRANSAKSI (buku kas utama: masuk/keluar/transfer/penyesuaian)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transaksi (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  tipe TEXT NOT NULL CHECK (tipe IN ('masuk','keluar','transfer','penyesuaian')),
  sumber TEXT NOT NULL DEFAULT 'manual'
    CHECK (sumber IN ('manual','iuran')),       -- pembeda transaksi hasil iuran vs input manual
  jumlah INTEGER NOT NULL,
  akun_id TEXT REFERENCES akun(id),             -- akun tunggal (masuk/keluar/penyesuaian)
  akun_asal_id TEXT REFERENCES akun(id),        -- khusus transfer
  akun_tujuan_id TEXT REFERENCES akun(id),      -- khusus transfer
  kategori_id TEXT REFERENCES kategori(id),
  metode_pembayaran TEXT DEFAULT 'Tunai'
    CHECK (metode_pembayaran IN ('Tunai','Transfer Bank','QRIS','E-Wallet')), -- NEW
  keterangan TEXT,
  catatan TEXT,
  no_referensi TEXT UNIQUE NOT NULL,            -- NEW: format TRX-YYYYMMDD-NNNN untuk struk
  tanggal TEXT NOT NULL,
  dicatat_oleh TEXT NOT NULL REFERENCES users(id),
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_transaksi_org ON transaksi(organization_id);
CREATE INDEX IF NOT EXISTS idx_transaksi_tanggal ON transaksi(organization_id, tanggal);
CREATE INDEX IF NOT EXISTS idx_transaksi_tipe ON transaksi(organization_id, tipe);

-- ------------------------------------------------------------
-- NOTIFIKASI (in-app saja — anggota menunggak/jatuh tempo/transaksi besar)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifikasi (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  tipe TEXT NOT NULL CHECK (tipe IN ('menunggak','jatuh_tempo','transaksi_besar')),
  judul TEXT NOT NULL,
  pesan TEXT NOT NULL,
  dibaca INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_notifikasi_org ON notifikasi(organization_id, dibaca);

-- ------------------------------------------------------------
-- KATEGORI DEFAULT (di-seed otomatis saat organisasi baru dibuat, lewat kode aplikasi)
-- Referensi saja, bukan dieksekusi di sini:
-- Konsumsi Kegiatan, Transportasi, Perlengkapan, Lainnya (tipe: pengeluaran)
-- ------------------------------------------------------------
