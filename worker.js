// ============================================================
// Aplikasi Kas Organisasi "Kas Kita" v2 - Cloudflare Worker
// ============================================================

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Org-Id',
      'Cache-Control': 'no-store',
    },
  });
}
function err(message, status = 400) { return json({ error: message }, status); }
function newId() { return crypto.randomUUID(); }
function inviteCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const arr = crypto.getRandomValues(new Uint8Array(8));
  let s = '';
  for (let i = 0; i < 8; i++) s += chars[arr[i] % chars.length];
  return s;
}

// ---- base64url ----
function bufToB64url(buf) {
  const bytes = new Uint8Array(buf);
  let bin = '';
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function b64urlToBuf(str) {
  str = str.replace(/-/g, '+').replace(/_/g, '/');
  while (str.length % 4) str += '=';
  const bin = atob(str);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return bytes.buffer;
}
const strToBuf = (s) => new TextEncoder().encode(s);
const bufToStr = (b) => new TextDecoder().decode(b);

// ---- password (PBKDF2) ----
async function hashPassword(password, saltB64) {
  const saltBytes = saltB64 ? new Uint8Array(b64urlToBuf(saltB64)) : crypto.getRandomValues(new Uint8Array(16));
  const km = await crypto.subtle.importKey('raw', strToBuf(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', salt: saltBytes, iterations: 100000, hash: 'SHA-256' }, km, 256);
  return { hash: bufToB64url(bits), salt: bufToB64url(saltBytes.buffer) };
}
async function verifyPassword(password, hash, salt) {
  const c = await hashPassword(password, salt);
  return c.hash === hash;
}

// ---- JWT (HMAC-SHA256) ----
async function signJwt(payload, secret) {
  const h = bufToB64url(strToBuf(JSON.stringify({ alg: 'HS256', typ: 'JWT' })));
  const p = bufToB64url(strToBuf(JSON.stringify(payload)));
  const data = `${h}.${p}`;
  const key = await crypto.subtle.importKey('raw', strToBuf(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const sig = await crypto.subtle.sign('HMAC', key, strToBuf(data));
  return `${data}.${bufToB64url(sig)}`;
}
async function verifyJwt(token, secret) {
  try {
    const [h, p, s] = token.split('.');
    const key = await crypto.subtle.importKey('raw', strToBuf(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['verify']);
    const ok = await crypto.subtle.verify('HMAC', key, b64urlToBuf(s), strToBuf(`${h}.${p}`));
    if (!ok) return null;
    const payload = JSON.parse(bufToStr(b64urlToBuf(p)));
    if (payload.exp && Date.now() / 1000 > payload.exp) return null;
    return payload;
  } catch (e) { return null; }
}

async function requireUser(request, env) {
  const auth = request.headers.get('Authorization') || '';
  const token = auth.startsWith('Bearer ') ? auth.slice(7) : null;
  if (!token) return null;
  const payload = await verifyJwt(token, env.JWT_SECRET);
  return payload && payload.uid ? payload.uid : null;
}
async function requireOrgMember(request, env, userId) {
  const orgId = request.headers.get('X-Org-Id');
  if (!orgId) return { error: err('X-Org-Id header wajib diisi', 400) };
  const member = await env.DB.prepare(
    'SELECT 1 FROM organization_members WHERE user_id = ? AND organization_id = ?'
  ).bind(userId, orgId).first();
  if (!member) return { error: err('Anda bukan anggota organisasi ini', 403) };
  return { orgId };
}

// ---- helper tanggal/periode ----
function todayStr() { return new Date().toISOString().slice(0, 10); }
function currentPeriode() { return new Date().toISOString().slice(0, 7); }
function periodeFromDateStr(d) { return (d || todayStr()).slice(0, 7); }
function periodAdd(periode, n) {
  const [y, m] = periode.split('-').map(Number);
  const total = y * 12 + (m - 1) + n;
  const ny = Math.floor(total / 12);
  const nm = (total % 12) + 1;
  return `${ny}-${String(nm).padStart(2, '0')}`;
}
function periodeMax(a, b) { return a > b ? a : b; } // format YYYY-MM aman dibanding string langsung
function monthsBetweenInclusive(a, b) {
  const [ay, am] = a.split('-').map(Number);
  const [by, bm] = b.split('-').map(Number);
  return (by * 12 + bm) - (ay * 12 + am) + 1;
}

const DEFAULT_KATEGORI = [
  { nama: 'Donasi', tipe: 'masuk' },
  { nama: 'Pendapatan Kegiatan', tipe: 'masuk' },
  { nama: 'Lain-lain (Masuk)', tipe: 'masuk' },
  { nama: 'Kegiatan', tipe: 'keluar' },
  { nama: 'Konsumsi', tipe: 'keluar' },
  { nama: 'Perlengkapan', tipe: 'keluar' },
  { nama: 'Transportasi', tipe: 'keluar' },
  { nama: 'Administrasi', tipe: 'keluar' },
  { nama: 'Sumbangan/Bantuan', tipe: 'keluar' },
  { nama: 'Perawatan', tipe: 'keluar' },
  { nama: 'Lainnya', tipe: 'keluar' },
];

// ---- Ambil startPeriode kewajiban seorang anggota ----
async function getAnggotaStartPeriode(env, orgId, anggota, settings) {
  const orgStart = periodeFromDateStr(settings.tanggal_mulai);
  const anggotaStart = periodeFromDateStr(anggota.tanggal_gabung);
  return periodeMax(orgStart, anggotaStart);
}

// ---- Ambil peta alokasi {periode: jumlah} untuk seorang anggota ----
async function getAlokasiMap(env, anggotaId) {
  const { results } = await env.DB.prepare(
    'SELECT periode, SUM(jumlah) as total FROM iuran_alokasi WHERE anggota_id = ? GROUP BY periode'
  ).bind(anggotaId).all();
  const map = {};
  results.forEach((r) => { map[r.periode] = r.total; });
  return map;
}

// ---- Hitung status + "lunas sampai" untuk satu anggota pada satu periode acuan ----
function computeStatusUntuk(periode, startPeriode, nominal, alokasiMap) {
  if (periode < startPeriode) return { status: 'tidak_dikenakan', dibayar: 0, wajib: 0 };
  const dibayar = alokasiMap[periode] || 0;
  let status;
  if (dibayar >= nominal - 0.01) status = 'lunas';
  else if (dibayar > 0) status = 'sebagian';
  else status = 'belum_bayar';
  return { status, dibayar, wajib: nominal };
}
function computeLunasSampai(startPeriode, nominal, alokasiMap, batasPeriode) {
  let p = startPeriode;
  let lunasSampai = null;
  let guard = 0;
  while (p <= batasPeriode && guard < 1200) {
    guard++;
    const dibayar = alokasiMap[p] || 0;
    if (dibayar >= nominal - 0.01) { lunasSampai = p; p = periodAdd(p, 1); }
    else break;
  }
  return lunasSampai;
}

// ---- Alokasikan pembayaran ke periode-periode (FIFO, mendukung top-up periode sebagian) ----
function hitungAlokasi(startPeriode, nominal, alokasiMapAwal, jumlahBayar) {
  const map = { ...alokasiMapAwal };
  let periode = startPeriode;
  let remaining = jumlahBayar;
  const hasil = [];
  let guard = 0;
  while (remaining > 0.009 && guard < 1200) {
    guard++;
    const sudah = map[periode] || 0;
    const butuh = nominal - sudah;
    if (butuh <= 0.009) { periode = periodAdd(periode, 1); continue; }
    const alokasi = Math.min(butuh, remaining);
    hasil.push({ periode, jumlah: alokasi });
    map[periode] = sudah + alokasi;
    remaining -= alokasi;
    if (alokasi < butuh - 0.009) break; // sisa pembayaran habis di tengah periode ini (sebagian)
    periode = periodAdd(periode, 1);
  }
  return hasil;
}

// ---- Saldo per akun ----
async function getSaldoAkun(env, orgId, akunId) {
  const akun = await env.DB.prepare('SELECT * FROM akun WHERE id = ? AND organization_id = ?').bind(akunId, orgId).first();
  if (!akun) return 0;
  const row = await env.DB.prepare(
    `SELECT
      COALESCE(SUM(CASE WHEN tipe='masuk' AND akun_id=? THEN jumlah ELSE 0 END),0) as masuk,
      COALESCE(SUM(CASE WHEN tipe='keluar' AND akun_id=? THEN jumlah ELSE 0 END),0) as keluar,
      COALESCE(SUM(CASE WHEN tipe='transfer' AND akun_id=? THEN jumlah ELSE 0 END),0) as transfer_keluar,
      COALESCE(SUM(CASE WHEN tipe='transfer' AND akun_tujuan_id=? THEN jumlah ELSE 0 END),0) as transfer_masuk,
      COALESCE(SUM(CASE WHEN tipe='penyesuaian' AND akun_id=? THEN jumlah ELSE 0 END),0) as penyesuaian
     FROM transaksi WHERE organization_id = ?`
  ).bind(akunId, akunId, akunId, akunId, akunId, orgId).first();
  return akun.saldo_awal + row.masuk - row.keluar - row.transfer_keluar + row.transfer_masuk + row.penyesuaian;
}

// ============================================================
// MAIN
// ============================================================
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;
    if (request.method === 'OPTIONS') return json({ ok: true });
    if (!path.startsWith('/api/')) {
      if (env.ASSETS) return env.ASSETS.fetch(request);
      return new Response('Not found', { status: 404 });
    }
    try {
      return await handleApi(request, env, path, url);
    } catch (e) {
      return err('Terjadi kesalahan server: ' + e.message, 500);
    }
  },
};

async function handleApi(request, env, path, url) {
  const method = request.method;

  // -------------------- PUBLIC --------------------
  if (path === '/api/register' && method === 'POST') {
    const { email, password, nama } = await request.json();
    if (!email || !password || !nama) return err('Email, password, dan nama wajib diisi');
    if (password.length < 6) return err('Password minimal 6 karakter');
    const existing = await env.DB.prepare('SELECT id FROM users WHERE email = ?').bind(email.toLowerCase()).first();
    if (existing) return err('Email sudah terdaftar', 409);
    const { hash, salt } = await hashPassword(password);
    const id = newId();
    await env.DB.prepare('INSERT INTO users (id, email, password_hash, password_salt, nama) VALUES (?, ?, ?, ?, ?)')
      .bind(id, email.toLowerCase(), hash, salt, nama).run();
    const token = await signJwt({ uid: id, exp: Math.floor(Date.now() / 1000) + 2592000 }, env.JWT_SECRET);
    return json({ token, user: { id, email: email.toLowerCase(), nama } });
  }

  if (path === '/api/login' && method === 'POST') {
    const { email, password } = await request.json();
    if (!email || !password) return err('Email dan password wajib diisi');
    const user = await env.DB.prepare('SELECT * FROM users WHERE email = ?').bind(email.toLowerCase()).first();
    if (!user || !(await verifyPassword(password, user.password_hash, user.password_salt))) return err('Email atau password salah', 401);
    const token = await signJwt({ uid: user.id, exp: Math.floor(Date.now() / 1000) + 2592000 }, env.JWT_SECRET);
    return json({ token, user: { id: user.id, email: user.email, nama: user.nama } });
  }

  const userId = await requireUser(request, env);
  if (!userId) return err('Unauthorized - silakan login', 401);

  // ---- Profil ----
  if (path === '/api/profil' && method === 'GET') {
    const user = await env.DB.prepare('SELECT id, email, nama FROM users WHERE id = ?').bind(userId).first();
    return json({ user });
  }
  if (path === '/api/profil' && method === 'PUT') {
    const { nama } = await request.json();
    if (!nama) return err('Nama wajib diisi');
    await env.DB.prepare('UPDATE users SET nama = ? WHERE id = ?').bind(nama, userId).run();
    return json({ ok: true });
  }
  if (path === '/api/password' && method === 'PUT') {
    const { password_lama, password_baru } = await request.json();
    if (!password_lama || !password_baru) return err('Password lama dan baru wajib diisi');
    if (password_baru.length < 6) return err('Password baru minimal 6 karakter');
    const user = await env.DB.prepare('SELECT * FROM users WHERE id = ?').bind(userId).first();
    if (!(await verifyPassword(password_lama, user.password_hash, user.password_salt))) return err('Password lama salah', 401);
    const { hash, salt } = await hashPassword(password_baru);
    await env.DB.prepare('UPDATE users SET password_hash = ?, password_salt = ? WHERE id = ?').bind(hash, salt, userId).run();
    return json({ ok: true });
  }

  // ---- Organisasi ----
  if (path === '/api/org/list' && method === 'GET') {
    const { results } = await env.DB.prepare(
      `SELECT o.id, o.nama, o.invite_code, o.created_at FROM organizations o
       JOIN organization_members m ON m.organization_id = o.id
       WHERE m.user_id = ? ORDER BY o.created_at DESC`
    ).bind(userId).all();
    return json({ organizations: results });
  }
  if (path === '/api/org/create' && method === 'POST') {
    const { nama } = await request.json();
    if (!nama) return err('Nama organisasi wajib diisi');
    const orgId = newId();
    let code = inviteCode();
    for (let i = 0; i < 5; i++) {
      const exists = await env.DB.prepare('SELECT 1 FROM organizations WHERE invite_code = ?').bind(code).first();
      if (!exists) break;
      code = inviteCode();
    }
    await env.DB.prepare('INSERT INTO organizations (id, nama, invite_code, created_by) VALUES (?, ?, ?, ?)').bind(orgId, nama, code, userId).run();
    await env.DB.prepare('INSERT INTO organization_members (id, user_id, organization_id) VALUES (?, ?, ?)').bind(newId(), userId, orgId).run();
    await env.DB.prepare('INSERT INTO iuran_settings (organization_id, nama_iuran, nominal, tanggal_mulai) VALUES (?, ?, 0, ?)')
      .bind(orgId, 'Iuran Bulanan', todayStr()).run();
    await env.DB.prepare('INSERT INTO akun (id, organization_id, nama, saldo_awal) VALUES (?, ?, ?, 0)').bind(newId(), orgId, 'Kas Utama').run();
    for (const k of DEFAULT_KATEGORI) {
      await env.DB.prepare('INSERT INTO kategori (id, organization_id, nama, tipe) VALUES (?, ?, ?, ?)').bind(newId(), orgId, k.nama, k.tipe).run();
    }
    return json({ organization: { id: orgId, nama, invite_code: code } });
  }
  if (path === '/api/org/join' && method === 'POST') {
    const { invite_code } = await request.json();
    if (!invite_code) return err('Kode undangan wajib diisi');
    const org = await env.DB.prepare('SELECT * FROM organizations WHERE invite_code = ?').bind(invite_code.toUpperCase()).first();
    if (!org) return err('Kode undangan tidak ditemukan', 404);
    const already = await env.DB.prepare('SELECT 1 FROM organization_members WHERE user_id = ? AND organization_id = ?').bind(userId, org.id).first();
    if (already) return err('Anda sudah tergabung di organisasi ini', 409);
    await env.DB.prepare('INSERT INTO organization_members (id, user_id, organization_id) VALUES (?, ?, ?)').bind(newId(), userId, org.id).run();
    return json({ organization: { id: org.id, nama: org.nama, invite_code: org.invite_code } });
  }

  // -------------------- BUTUH X-Org-Id --------------------
  const orgCheck = await requireOrgMember(request, env, userId);
  if (orgCheck.error) return orgCheck.error;
  const orgId = orgCheck.orgId;

  // ---- Akun ----
  if (path === '/api/akun' && method === 'GET') {
    const { results } = await env.DB.prepare('SELECT * FROM akun WHERE organization_id = ? AND aktif = 1 ORDER BY created_at').bind(orgId).all();
    const withSaldo = [];
    for (const a of results) withSaldo.push({ ...a, saldo: await getSaldoAkun(env, orgId, a.id) });
    return json({ akun: withSaldo });
  }
  if (path === '/api/akun' && method === 'POST') {
    const { nama, saldo_awal } = await request.json();
    if (!nama) return err('Nama akun wajib diisi');
    const id = newId();
    await env.DB.prepare('INSERT INTO akun (id, organization_id, nama, saldo_awal) VALUES (?, ?, ?, ?)').bind(id, orgId, nama, saldo_awal || 0).run();
    return json({ id, nama });
  }
  if (path.match(/^\/api\/akun\/[^/]+$/) && method === 'PUT') {
    const id = path.split('/').pop();
    const { nama, saldo_awal } = await request.json();
    if (!nama) return err('Nama akun wajib diisi');
    await env.DB.prepare('UPDATE akun SET nama = ?, saldo_awal = ? WHERE id = ? AND organization_id = ?')
      .bind(nama, saldo_awal || 0, id, orgId).run();
    return json({ ok: true });
  }
  if (path.match(/^\/api\/akun\/[^/]+$/) && method === 'DELETE') {
    const id = path.split('/').pop();
    const dipakai = await env.DB.prepare('SELECT 1 FROM transaksi WHERE (akun_id = ? OR akun_tujuan_id = ?) AND organization_id = ? LIMIT 1').bind(id, id, orgId).first();
    if (dipakai) return err('Akun tidak bisa dihapus karena sudah punya riwayat transaksi. Nonaktifkan saja lewat Edit.', 400);
    const jumlahAkun = await env.DB.prepare('SELECT COUNT(*) as c FROM akun WHERE organization_id = ? AND aktif = 1').bind(orgId).first();
    if (jumlahAkun.c <= 1) return err('Tidak bisa menghapus akun terakhir. Organisasi minimal punya 1 akun.', 400);
    await env.DB.prepare('UPDATE akun SET aktif = 0 WHERE id = ? AND organization_id = ?').bind(id, orgId).run();
    return json({ ok: true });
  }

  // ---- Anggota ----
  if (path === '/api/anggota' && method === 'GET') {
    const { results } = await env.DB.prepare('SELECT * FROM anggota WHERE organization_id = ? ORDER BY nama ASC').bind(orgId).all();
    return json({ anggota: results });
  }
  if (path === '/api/anggota' && method === 'POST') {
    const { nama, no_hp, catatan, tanggal_gabung } = await request.json();
    if (!nama) return err('Nama anggota wajib diisi');
    const id = newId();
    await env.DB.prepare('INSERT INTO anggota (id, organization_id, nama, no_hp, catatan, tanggal_gabung) VALUES (?, ?, ?, ?, ?, ?)')
      .bind(id, orgId, nama, no_hp || null, catatan || null, tanggal_gabung || todayStr()).run();
    return json({ id, nama });
  }
  if (path.match(/^\/api\/anggota\/[^/]+$/) && method === 'PUT') {
    const id = path.split('/').pop();
    const { nama, no_hp, catatan, aktif, tanggal_gabung } = await request.json();
    await env.DB.prepare('UPDATE anggota SET nama=?, no_hp=?, catatan=?, aktif=?, tanggal_gabung=? WHERE id=? AND organization_id=?')
      .bind(nama, no_hp || null, catatan || null, aktif === undefined ? 1 : (aktif ? 1 : 0), tanggal_gabung || todayStr(), id, orgId).run();
    return json({ ok: true });
  }
  if (path.match(/^\/api\/anggota\/[^/]+$/) && method === 'DELETE') {
    const id = path.split('/').pop();
    await env.DB.prepare('DELETE FROM anggota WHERE id = ? AND organization_id = ?').bind(id, orgId).run();
    return json({ ok: true });
  }

  // ---- Kategori ----
  if (path === '/api/kategori' && method === 'GET') {
    const { results } = await env.DB.prepare('SELECT * FROM kategori WHERE organization_id = ? ORDER BY tipe, nama').bind(orgId).all();
    return json({ kategori: results });
  }
  if (path === '/api/kategori' && method === 'POST') {
    const { nama, tipe } = await request.json();
    if (!nama || !['masuk', 'keluar'].includes(tipe)) return err('Nama dan tipe wajib diisi');
    const id = newId();
    await env.DB.prepare('INSERT INTO kategori (id, organization_id, nama, tipe) VALUES (?, ?, ?, ?)').bind(id, orgId, nama, tipe).run();
    return json({ id, nama, tipe });
  }
  if (path.match(/^\/api\/kategori\/[^/]+$/) && method === 'DELETE') {
    const id = path.split('/').pop();
    await env.DB.prepare('DELETE FROM kategori WHERE id = ? AND organization_id = ?').bind(id, orgId).run();
    return json({ ok: true });
  }

  // ---- Iuran Settings ----
  if (path === '/api/iuran-settings' && method === 'GET') {
    const s = await env.DB.prepare('SELECT * FROM iuran_settings WHERE organization_id = ?').bind(orgId).first();
    return json({ settings: s });
  }
  if (path === '/api/iuran-settings' && method === 'PUT') {
    const { nama_iuran, nominal, tanggal_mulai } = await request.json();
    if (nominal === undefined || nominal < 0) return err('Nominal iuran tidak valid');
    if (!tanggal_mulai) return err('Tanggal mulai iuran wajib diisi');
    await env.DB.prepare('UPDATE iuran_settings SET nama_iuran=?, nominal=?, tanggal_mulai=?, updated_at=datetime("now") WHERE organization_id=?')
      .bind(nama_iuran || 'Iuran Bulanan', nominal, tanggal_mulai, orgId).run();
    return json({ ok: true });
  }

  // ---- Kas: Transaksi umum (masuk/keluar/transfer/penyesuaian) ----
  if (path === '/api/transaksi' && method === 'GET') {
    const from = url.searchParams.get('from');
    const to = url.searchParams.get('to');
    const filterTipe = url.searchParams.get('tipe'); // masuk|keluar|transfer|penyesuaian
    let q = `SELECT t.*, a.nama as anggota_nama, ak.nama as akun_nama, ak2.nama as akun_tujuan_nama
              FROM transaksi t
              LEFT JOIN anggota a ON a.id = t.anggota_id
              LEFT JOIN akun ak ON ak.id = t.akun_id
              LEFT JOIN akun ak2 ON ak2.id = t.akun_tujuan_id
              WHERE t.organization_id = ?`;
    const params = [orgId];
    if (from) { q += ' AND t.tanggal >= ?'; params.push(from); }
    if (to) { q += ' AND t.tanggal <= ?'; params.push(to); }
    if (filterTipe) { q += ' AND t.tipe = ?'; params.push(filterTipe); }
    q += ' ORDER BY t.tanggal DESC, t.created_at DESC LIMIT 500';
    const { results } = await env.DB.prepare(q).bind(...params).all();
    return json({ transaksi: results });
  }

  if (path === '/api/transaksi' && method === 'POST') {
    const { tipe, kategori, jumlah, catatan, metode, tanggal, akun_id, akun_tujuan_id } = await request.json();
    if (!['masuk', 'keluar', 'transfer', 'penyesuaian'].includes(tipe)) return err('Tipe transaksi tidak valid');
    if (jumlah === undefined || jumlah === null) return err('Jumlah wajib diisi');
    if (tipe !== 'penyesuaian' && jumlah <= 0) return err('Jumlah harus lebih dari 0');
    if (tipe === 'transfer') {
      if (!akun_id || !akun_tujuan_id) return err('Akun asal dan tujuan wajib dipilih untuk transfer');
      if (akun_id === akun_tujuan_id) return err('Akun asal dan tujuan tidak boleh sama');
    }
    if (tipe === 'penyesuaian' && !catatan) return err('Keterangan wajib diisi untuk penyesuaian saldo');
    const id = newId();
    const tgl = tanggal || todayStr();
    await env.DB.prepare(
      `INSERT INTO transaksi (id, organization_id, tipe, sumber, kategori, jumlah, catatan, metode, akun_id, akun_tujuan_id, tanggal, created_by)
       VALUES (?, ?, ?, 'umum', ?, ?, ?, ?, ?, ?, ?, ?)`
    ).bind(id, orgId, tipe, kategori || null, jumlah, catatan || null, metode || null, akun_id || null, akun_tujuan_id || null, tgl, userId).run();
    const org = await env.DB.prepare('SELECT nama FROM organizations WHERE id = ?').bind(orgId).first();
    return json({ transaksi: { id, tipe, kategori, jumlah, catatan, tanggal: tgl, organisasi: org.nama } });
  }

  if (path.match(/^\/api\/transaksi\/[^/]+$/) && method === 'DELETE') {
    const id = path.split('/').pop();
    const t = await env.DB.prepare('SELECT sumber FROM transaksi WHERE id = ? AND organization_id = ?').bind(id, orgId).first();
    if (t && t.sumber === 'iuran') return err('Transaksi iuran tidak bisa dihapus langsung dari Kas. Hapus lewat riwayat pembayaran di menu Iuran.', 400);
    await env.DB.prepare('DELETE FROM transaksi WHERE id = ? AND organization_id = ?').bind(id, orgId).run();
    return json({ ok: true });
  }

  if (path.match(/^\/api\/transaksi\/[^/]+\/struk$/) && method === 'GET') {
    const id = path.split('/')[3];
    const t = await env.DB.prepare(
      `SELECT t.*, a.nama as anggota_nama FROM transaksi t LEFT JOIN anggota a ON a.id = t.anggota_id
       WHERE t.id = ? AND t.organization_id = ?`
    ).bind(id, orgId).first();
    if (!t) return err('Transaksi tidak ditemukan', 404);
    const org = await env.DB.prepare('SELECT nama FROM organizations WHERE id = ?').bind(orgId).first();
    let periodeList = [];
    if (t.sumber === 'iuran') {
      const pemb = await env.DB.prepare('SELECT id FROM iuran_pembayaran WHERE transaksi_id = ?').bind(id).first();
      if (pemb) {
        const pr = await env.DB.prepare('SELECT periode FROM iuran_alokasi WHERE pembayaran_id = ? ORDER BY periode').bind(pemb.id).all();
        periodeList = pr.results.map((r) => r.periode);
      }
    }
    return json({ struk: { ...t, organisasi: org.nama, periode_list: periodeList } });
  }

  // ---- Iuran: status per periode (untuk semua anggota) ----
  if (path === '/api/iuran/status' && method === 'GET') {
    const periode = url.searchParams.get('periode') || currentPeriode();
    const settings = await env.DB.prepare('SELECT * FROM iuran_settings WHERE organization_id = ?').bind(orgId).first();
    const { results: semuaAnggota } = await env.DB.prepare('SELECT * FROM anggota WHERE organization_id = ? AND aktif = 1 ORDER BY nama').bind(orgId).all();
    const status = [];
    for (const a of semuaAnggota) {
      const startPeriode = await getAnggotaStartPeriode(env, orgId, a, settings);
      const alokasiMap = await getAlokasiMap(env, a.id);
      const s = computeStatusUntuk(periode, startPeriode, settings.nominal, alokasiMap);
      const lunasSampai = computeLunasSampai(startPeriode, settings.nominal, alokasiMap, periode > currentPeriode() ? periode : currentPeriode());
      status.push({ anggota_id: a.id, nama: a.nama, no_hp: a.no_hp, status: s.status, dibayar: s.dibayar, wajib: s.wajib, lunas_sampai: lunasSampai });
    }
    const lunas = status.filter((s) => s.status === 'lunas').length;
    const sebagian = status.filter((s) => s.status === 'sebagian').length;
    const belum = status.filter((s) => s.status === 'belum_bayar').length;
    const terkumpul = status.reduce((sum, s) => sum + (s.status !== 'tidak_dikenakan' ? s.dibayar : 0), 0);
    const tunggakan = status.reduce((sum, s) => sum + (s.status !== 'tidak_dikenakan' ? Math.max(0, s.wajib - s.dibayar) : 0), 0);
    return json({ periode, total_anggota: status.length, lunas, sebagian, menunggak: belum, terkumpul, tunggakan, status });
  }

  // ---- Iuran: bayar (mendukung multi-periode + pembayaran sebagian, FIFO otomatis) ----
  if (path === '/api/iuran/bayar' && method === 'POST') {
    const { anggota_id, jumlah, catatan, tanggal, akun_id } = await request.json();
    if (!anggota_id) return err('Anggota wajib dipilih');
    if (!jumlah || jumlah <= 0) return err('Jumlah pembayaran harus lebih dari 0');
    const anggota = await env.DB.prepare('SELECT * FROM anggota WHERE id = ? AND organization_id = ?').bind(anggota_id, orgId).first();
    if (!anggota) return err('Anggota tidak ditemukan', 404);
    const settings = await env.DB.prepare('SELECT * FROM iuran_settings WHERE organization_id = ?').bind(orgId).first();
    if (!settings || settings.nominal <= 0) return err('Nominal iuran belum diatur. Atur di menu Pengaturan Iuran.');

    const startPeriode = await getAnggotaStartPeriode(env, orgId, anggota, settings);
    const alokasiMap = await getAlokasiMap(env, anggota_id);
    const alokasiHasil = hitungAlokasi(startPeriode, settings.nominal, alokasiMap, jumlah);
    if (alokasiHasil.length === 0) return err('Tidak ada alokasi yang bisa dibuat (periode mungkin sudah lunas semua)');

    const tgl = tanggal || todayStr();
    const org = await env.DB.prepare('SELECT nama FROM organizations WHERE id = ?').bind(orgId).first();
    const defaultAkun = akun_id || (await env.DB.prepare('SELECT id FROM akun WHERE organization_id = ? ORDER BY created_at LIMIT 1').bind(orgId).first())?.id;

    // 1 transaksi Kas per pembayaran (tidak duplikat)
    const transaksiId = newId();
    await env.DB.prepare(
      `INSERT INTO transaksi (id, organization_id, tipe, sumber, kategori, jumlah, catatan, akun_id, anggota_id, tanggal, created_by)
       VALUES (?, ?, 'masuk', 'iuran', ?, ?, ?, ?, ?, ?, ?)`
    ).bind(transaksiId, orgId, settings.nama_iuran || 'Iuran Anggota', jumlah, catatan || `Iuran (${anggota.nama})`, defaultAkun || null, anggota_id, tgl, userId).run();

    const pembayaranId = newId();
    await env.DB.prepare(
      `INSERT INTO iuran_pembayaran (id, organization_id, anggota_id, jumlah_total, tanggal_bayar, transaksi_id, catatan)
       VALUES (?, ?, ?, ?, ?, ?, ?)`
    ).bind(pembayaranId, orgId, anggota_id, jumlah, tgl, transaksiId, catatan || null).run();

    for (const a of alokasiHasil) {
      await env.DB.prepare(
        'INSERT INTO iuran_alokasi (id, organization_id, anggota_id, pembayaran_id, periode, jumlah) VALUES (?, ?, ?, ?, ?, ?)'
      ).bind(newId(), orgId, anggota_id, pembayaranId, a.periode, a.jumlah).run();
    }

    const lunasSampai = alokasiHasil[alokasiHasil.length - 1];
    const statusTerakhir = (lunasSampai.jumlah >= settings.nominal - 0.01) ? lunasSampai.periode : (alokasiHasil.length > 1 ? alokasiHasil[alokasiHasil.length - 2].periode : null);

    return json({
      transaksi: {
        id: transaksiId, anggota: anggota.nama, jumlah, tanggal: tgl, organisasi: org.nama,
        periode_list: alokasiHasil.map((a) => a.periode),
      },
      lunas_sampai: statusTerakhir,
    });
  }

  // ---- Iuran: riwayat pembayaran seorang anggota ----
  if (path.match(/^\/api\/iuran\/riwayat\/[^/]+$/) && method === 'GET') {
    const anggotaId = path.split('/').pop();
    const { results } = await env.DB.prepare(
      'SELECT * FROM iuran_pembayaran WHERE anggota_id = ? AND organization_id = ? ORDER BY tanggal_bayar DESC'
    ).bind(anggotaId, orgId).all();
    for (const r of results) {
      const al = await env.DB.prepare('SELECT periode, jumlah FROM iuran_alokasi WHERE pembayaran_id = ? ORDER BY periode').bind(r.id).all();
      r.alokasi = al.results;
    }
    return json({ riwayat: results });
  }

  // ---- Laporan tunggakan khusus ----
  if (path === '/api/laporan/tunggakan' && method === 'GET') {
    const periode = url.searchParams.get('periode') || currentPeriode();
    const settings = await env.DB.prepare('SELECT * FROM iuran_settings WHERE organization_id = ?').bind(orgId).first();
    const { results: semuaAnggota } = await env.DB.prepare('SELECT * FROM anggota WHERE organization_id = ? AND aktif = 1 ORDER BY nama').bind(orgId).all();
    const menunggak = [];
    for (const a of semuaAnggota) {
      const startPeriode = await getAnggotaStartPeriode(env, orgId, a, settings);
      if (periode < startPeriode) continue;
      const alokasiMap = await getAlokasiMap(env, a.id);
      let totalTunggakan = 0;
      let p = startPeriode;
      let guard = 0;
      while (p <= periode && guard < 1200) {
        guard++;
        const dibayar = alokasiMap[p] || 0;
        totalTunggakan += Math.max(0, settings.nominal - dibayar);
        p = periodAdd(p, 1);
      }
      if (totalTunggakan > 0.01) menunggak.push({ anggota_id: a.id, nama: a.nama, no_hp: a.no_hp, total_tunggakan: totalTunggakan });
    }
    return json({ periode, menunggak, jumlah: menunggak.length });
  }

  // ---- Laporan bulanan (hanya masuk/keluar dihitung, transfer & penyesuaian dikecualikan dari income/expense) ----
  if (path === '/api/laporan/bulanan' && method === 'GET') {
    const bulan = url.searchParams.get('bulan') || currentPeriode();
    const { results: trx } = await env.DB.prepare(
      'SELECT * FROM transaksi WHERE organization_id = ? AND substr(tanggal,1,7) = ? ORDER BY tanggal'
    ).bind(orgId, bulan).all();
    const totalMasuk = trx.filter((t) => t.tipe === 'masuk').reduce((a, t) => a + t.jumlah, 0);
    const totalKeluar = trx.filter((t) => t.tipe === 'keluar').reduce((a, t) => a + t.jumlah, 0);
    const perKategori = {};
    trx.forEach((t) => {
      if (t.tipe !== 'masuk' && t.tipe !== 'keluar') return;
      const k = t.kategori || 'Lainnya';
      if (!perKategori[k]) perKategori[k] = { masuk: 0, keluar: 0 };
      perKategori[k][t.tipe] += t.jumlah;
    });
    const iuran = await (await handleApi(request, env, '/api/iuran/status?periode=' + bulan, new URL(url.origin + '/api/iuran/status?periode=' + bulan))).json();
    return json({
      bulan, total_masuk: totalMasuk, total_keluar: totalKeluar, saldo_bersih: totalMasuk - totalKeluar,
      jumlah_transaksi: trx.length, per_kategori: perKategori,
      iuran_lunas: iuran.lunas, iuran_sebagian: iuran.sebagian, iuran_menunggak: iuran.menunggak,
      iuran_terkumpul: iuran.terkumpul, iuran_tunggakan: iuran.tunggakan, total_anggota: iuran.total_anggota,
      transaksi: trx,
    });
  }

  // ---- Laporan tahunan ----
  if (path === '/api/laporan/tahunan' && method === 'GET') {
    const tahun = url.searchParams.get('tahun') || String(new Date().getFullYear());
    const { results: trx } = await env.DB.prepare('SELECT * FROM transaksi WHERE organization_id = ? AND substr(tanggal,1,4) = ? ORDER BY tanggal').bind(orgId, tahun).all();
    const perBulan = {};
    for (let i = 1; i <= 12; i++) perBulan[`${tahun}-${String(i).padStart(2, '0')}`] = { masuk: 0, keluar: 0 };
    trx.forEach((t) => {
      if (t.tipe !== 'masuk' && t.tipe !== 'keluar') return;
      const key = t.tanggal.slice(0, 7);
      if (!perBulan[key]) perBulan[key] = { masuk: 0, keluar: 0 };
      perBulan[key][t.tipe] += t.jumlah;
    });
    const totalMasuk = trx.filter((t) => t.tipe === 'masuk').reduce((a, t) => a + t.jumlah, 0);
    const totalKeluar = trx.filter((t) => t.tipe === 'keluar').reduce((a, t) => a + t.jumlah, 0);
    return json({ tahun, total_masuk: totalMasuk, total_keluar: totalKeluar, saldo_bersih: totalMasuk - totalKeluar, jumlah_transaksi: trx.length, per_bulan: perBulan });
  }

  // ---- Beranda: ringkasan dashboard ----
  if (path === '/api/beranda' && method === 'GET') {
    const bulan = currentPeriode();
    const { results: akunList } = await env.DB.prepare('SELECT id FROM akun WHERE organization_id = ? AND aktif = 1').bind(orgId).all();
    let saldoTotal = 0;
    for (const a of akunList) saldoTotal += await getSaldoAkun(env, orgId, a.id);
    const { results: trxBulan } = await env.DB.prepare('SELECT * FROM transaksi WHERE organization_id = ? AND substr(tanggal,1,7) = ?').bind(orgId, bulan).all();
    const totalMasuk = trxBulan.filter((t) => t.tipe === 'masuk').reduce((a, t) => a + t.jumlah, 0);
    const totalKeluar = trxBulan.filter((t) => t.tipe === 'keluar').reduce((a, t) => a + t.jumlah, 0);
    const settings = await env.DB.prepare('SELECT * FROM iuran_settings WHERE organization_id = ?').bind(orgId).first();
    const { results: semuaAnggota } = await env.DB.prepare('SELECT * FROM anggota WHERE organization_id = ? AND aktif = 1').bind(orgId).all();
    let lunas = 0, sebagian = 0, belum = 0, terkumpul = 0, tunggakan = 0;
    for (const a of semuaAnggota) {
      const startPeriode = await getAnggotaStartPeriode(env, orgId, a, settings);
      const alokasiMap = await getAlokasiMap(env, a.id);
      const s = computeStatusUntuk(bulan, startPeriode, settings.nominal, alokasiMap);
      if (s.status === 'lunas') lunas++;
      else if (s.status === 'sebagian') sebagian++;
      else if (s.status === 'belum_bayar') belum++;
      if (s.status !== 'tidak_dikenakan') { terkumpul += s.dibayar; tunggakan += Math.max(0, s.wajib - s.dibayar); }
    }
    const { results: aktivitas } = await env.DB.prepare(
      `SELECT t.*, a.nama as anggota_nama FROM transaksi t LEFT JOIN anggota a ON a.id = t.anggota_id
       WHERE t.organization_id = ? ORDER BY t.created_at DESC LIMIT 5`
    ).bind(orgId).all();
    return json({
      saldo: saldoTotal, pemasukan_bulan_ini: totalMasuk, pengeluaran_bulan_ini: totalKeluar,
      iuran: { total_anggota: semuaAnggota.length, lunas, sebagian, belum, terkumpul, tunggakan },
      aktivitas_terbaru: aktivitas,
    });
  }

  return err('Endpoint tidak ditemukan', 404);
}
