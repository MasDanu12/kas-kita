-- ============================================================
-- Kas Kita - Migrasi D1 dari schema transaksi lama ke v2
-- Aman untuk data lama: TIDAK menghapus tabel atau transaksi.
-- Jalankan per blok di Cloudflare D1 Console.
-- ============================================================

-- BLOK 1: tambahkan kolom yang dibutuhkan worker v2 pada tabel transaksi lama.
-- Jalankan SATU PER SATU.
ALTER TABLE transaksi ADD COLUMN sumber TEXT NOT NULL DEFAULT 'umum';
ALTER TABLE transaksi ADD COLUMN akun_id TEXT;
ALTER TABLE transaksi ADD COLUMN akun_tujuan_id TEXT;

-- BLOK 2: pastikan tabel pendukung v2 tersedia.
CREATE TABLE IF NOT EXISTS akun (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL,
  nama TEXT NOT NULL,
  saldo_awal REAL NOT NULL DEFAULT 0,
  aktif INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE
);

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

CREATE TABLE IF NOT EXISTS iuran_alokasi (
  id TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL,
  anggota_id TEXT NOT NULL,
  pembayaran_id TEXT NOT NULL,
  periode TEXT NOT NULL,
  jumlah REAL NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE,
  FOREIGN KEY (anggota_id) REFERENCES anggota(id) ON DELETE CASCADE,
  FOREIGN KEY (pembayaran_id) REFERENCES iuran_pembayaran(id) ON DELETE CASCADE
);

-- BLOK 3: lengkapi data organisasi lama.
-- Organisasi lama akan mendapat Kas Utama jika belum punya akun.
INSERT INTO akun (id, organization_id, nama, saldo_awal)
SELECT lower(hex(randomblob(16))), o.id, 'Kas Utama', 0
FROM organizations o
WHERE NOT EXISTS (
  SELECT 1 FROM akun a WHERE a.organization_id = o.id
);

-- Organisasi lama mendapat pengaturan iuran jika belum punya.
INSERT INTO iuran_settings (organization_id, nama_iuran, nominal, tanggal_mulai)
SELECT o.id, 'Iuran Bulanan', 0, date(o.created_at)
FROM organizations o
WHERE NOT EXISTS (
  SELECT 1 FROM iuran_settings s WHERE s.organization_id = o.id
);

-- BLOK 4: indeks.
CREATE INDEX IF NOT EXISTS idx_akun_org ON akun(organization_id);
CREATE INDEX IF NOT EXISTS idx_transaksi_akun ON transaksi(akun_id);
CREATE INDEX IF NOT EXISTS idx_transaksi_org ON transaksi(organization_id, tanggal);
CREATE INDEX IF NOT EXISTS idx_iuran_pemb_org ON iuran_pembayaran(organization_id, anggota_id);
CREATE INDEX IF NOT EXISTS idx_iuran_alokasi_lookup ON iuran_alokasi(anggota_id, periode);
CREATE INDEX IF NOT EXISTS idx_iuran_alokasi_org ON iuran_alokasi(organization_id, periode);

-- Catatan:
-- 1. Jika ALTER TABLE tertentu menjawab "duplicate column name", kolom itu
--    sudah ada. Jangan jalankan ulang perintah ALTER tersebut.
-- 2. Jangan DROP TABLE dan jangan menghapus database produksi.
-- 3. Setelah migrasi selesai, deploy worker.js terbaru.
