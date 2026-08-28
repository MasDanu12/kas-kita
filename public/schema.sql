-- ============================================================
-- Skema Database: Aplikasi Kas Organisasi "Kas Kita" (v2)
-- Cloudflare D1 (SQLite)
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  password_salt TEXT NOT NULL,
  nama TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS organizations (
  id TEXT PRIMARY KEY,
  nama TEXT NOT NULL,
  invite_code TEXT UNIQUE NOT NULL,
  created_by TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (created_by) REFERENCES users(id)
);

-- Semua member organisasi punya akses sama (tidak ada role bertingkat)
CREATE TABLE IF NOT EXISTS organization_members (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  organization_id TEXT NOT NULL,
  joined_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
  UNIQUE(user_id, organization_id)
);

-- Akun/tempat penyimpanan uang (Kas Tunai, Bank, dst) - tiap org otomatis punya 1 akun default
CREATE TABLE IF NOT EXISTS akun (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL,
  nama TEXT NOT NULL,
  saldo_awal REAL NOT NULL DEFAULT 0,
  aktif INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

-- Anggota lembaga (bukan akun login). tanggal_gabung = mulai kewajiban iuran.
CREATE TABLE IF NOT EXISTS anggota (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL,
  nama TEXT NOT NULL,
  no_hp TEXT,
  catatan TEXT,
  tanggal_gabung TEXT NOT NULL,
  aktif INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

-- Pengaturan iuran bulanan per organisasi. tanggal_mulai = titik awal periode iuran organisasi.
CREATE TABLE IF NOT EXISTS iuran_settings (
  organization_id TEXT PRIMARY KEY,
  nama_iuran TEXT NOT NULL DEFAULT 'Iuran Bulanan',
  nominal REAL NOT NULL DEFAULT 0,
  tanggal_mulai TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS kategori (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL,
  nama TEXT NOT NULL,
  tipe TEXT NOT NULL CHECK (tipe IN ('masuk','keluar')),
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

-- Buku Kas: masuk, keluar, transfer (antar akun, netral di level org), penyesuaian (koreksi saldo akun)
CREATE TABLE IF NOT EXISTS transaksi (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL,
  tipe TEXT NOT NULL CHECK (tipe IN ('masuk','keluar','transfer','penyesuaian')),
  sumber TEXT NOT NULL DEFAULT 'umum' CHECK (sumber IN ('umum','iuran')),
  kategori TEXT,
  jumlah REAL NOT NULL,
  catatan TEXT,
  metode TEXT,
  anggota_id TEXT,
  akun_id TEXT,
  akun_tujuan_id TEXT,
  tanggal TEXT NOT NULL,
  created_by TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
  FOREIGN KEY (anggota_id) REFERENCES anggota(id) ON DELETE SET NULL,
  FOREIGN KEY (akun_id) REFERENCES akun(id) ON DELETE SET NULL,
  FOREIGN KEY (akun_tujuan_id) REFERENCES akun(id) ON DELETE SET NULL,
  FOREIGN KEY (created_by) REFERENCES users(id)
);

-- Ledger pembayaran iuran (satu baris per event pembayaran, terhubung 1:1 ke satu transaksi Kas - tidak boleh duplikat)
CREATE TABLE IF NOT EXISTS iuran_pembayaran (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL,
  anggota_id TEXT NOT NULL,
  jumlah_total REAL NOT NULL,
  tanggal_bayar TEXT NOT NULL,
  transaksi_id TEXT NOT NULL UNIQUE,
  catatan TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
  FOREIGN KEY (anggota_id) REFERENCES anggota(id) ON DELETE CASCADE,
  FOREIGN KEY (transaksi_id) REFERENCES transaksi(id) ON DELETE CASCADE
);

-- Alokasi tiap pembayaran ke periode (YYYY-MM) tertentu - mendukung multi-bulan & pembayaran sebagian (FIFO)
CREATE TABLE IF NOT EXISTS iuran_alokasi (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL,
  anggota_id TEXT NOT NULL,
  pembayaran_id TEXT NOT NULL,
  periode TEXT NOT NULL, -- 'YYYY-MM'
  jumlah REAL NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
  FOREIGN KEY (anggota_id) REFERENCES anggota(id) ON DELETE CASCADE,
  FOREIGN KEY (pembayaran_id) REFERENCES iuran_pembayaran(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_members_user ON organization_members(user_id);
CREATE INDEX IF NOT EXISTS idx_members_org ON organization_members(organization_id);
CREATE INDEX IF NOT EXISTS idx_akun_org ON akun(organization_id);
CREATE INDEX IF NOT EXISTS idx_anggota_org ON anggota(organization_id);
CREATE INDEX IF NOT EXISTS idx_transaksi_org ON transaksi(organization_id, tanggal);
CREATE INDEX IF NOT EXISTS idx_transaksi_akun ON transaksi(akun_id);
CREATE INDEX IF NOT EXISTS idx_iuran_pemb_org ON iuran_pembayaran(organization_id, anggota_id);
CREATE INDEX IF NOT EXISTS idx_iuran_alokasi_lookup ON iuran_alokasi(anggota_id, periode);
CREATE INDEX IF NOT EXISTS idx_iuran_alokasi_org ON iuran_alokasi(organization_id, periode);
