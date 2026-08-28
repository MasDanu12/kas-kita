// ============================================================
// Aplikasi Kas Organisasi - Cloudflare Worker
// Backend: D1 (SQLite). Auth: email+password (PBKDF2) + JWT (HMAC).
// ============================================================

// ------------------------- UTIL: RESPONSE -------------------------
function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Org-Id',
    },
  });
}
function err(message, status = 400) {
  return json({ error: message }, status);
}

// ------------------------- UTIL: ID -------------------------
function newId() {
  return crypto.randomUUID();
}
function inviteCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // tanpa karakter mirip
  let s = '';
  const arr = crypto.getRandomValues(new Uint8Array(8));
  for (let i = 0; i < 8; i++) s += chars[arr[i] % chars.length];
  return s;
}

// ------------------------- UTIL: BASE64URL -------------------------
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
function strToBuf(str) {
  return new TextEncoder().encode(str);
}
function bufToStr(buf) {
  return new TextDecoder().decode(buf);
}

// ------------------------- UTIL: PASSWORD HASH (PBKDF2) -------------------------
async function hashPassword(password, saltB64) {
  let saltBytes;
  if (saltB64) {
    saltBytes = new Uint8Array(b64urlToBuf(saltB64));
  } else {
    saltBytes = crypto.getRandomValues(new Uint8Array(16));
  }
  const keyMaterial = await crypto.subtle.importKey(
    'raw', strToBuf(password), 'PBKDF2', false, ['deriveBits']
  );
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt: saltBytes, iterations: 100000, hash: 'SHA-256' },
    keyMaterial, 256
  );
  return { hash: bufToB64url(bits), salt: bufToB64url(saltBytes.buffer) };
}
async function verifyPassword(password, hash, salt) {
  const check = await hashPassword(password, salt);
  return check.hash === hash;
}

// ------------------------- UTIL: JWT (HMAC-SHA256) -------------------------
async function signJwt(payload, secret) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const headerB64 = bufToB64url(strToBuf(JSON.stringify(header)));
  const payloadB64 = bufToB64url(strToBuf(JSON.stringify(payload)));
  const data = `${headerB64}.${payloadB64}`;
  const key = await crypto.subtle.importKey(
    'raw', strToBuf(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
  );
  const sig = await crypto.subtle.sign('HMAC', key, strToBuf(data));
  return `${data}.${bufToB64url(sig)}`;
}
async function verifyJwt(token, secret) {
  try {
    const [headerB64, payloadB64, sigB64] = token.split('.');
    const data = `${headerB64}.${payloadB64}`;
    const key = await crypto.subtle.importKey(
      'raw', strToBuf(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['verify']
    );
    const valid = await crypto.subtle.verify('HMAC', key, b64urlToBuf(sigB64), strToBuf(data));
    if (!valid) return null;
    const payload = JSON.parse(bufToStr(b64urlToBuf(payloadB64)));
    if (payload.exp && Date.now() / 1000 > payload.exp) return null;
    return payload;
  } catch (e) {
    return null;
  }
}

// ------------------------- AUTH MIDDLEWARE -------------------------
async function requireUser(request, env) {
  const auth = request.headers.get('Authorization') || '';
  const token = auth.startsWith('Bearer ') ? auth.slice(7) : null;
  if (!token) return null;
  const payload = await verifyJwt(token, env.JWT_SECRET);
  if (!payload || !payload.uid) return null;
  return payload.uid;
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

// ------------------------- HELPERS -------------------------
function periodAdd(periode, n) {
  // periode format 'YYYY-MM', tambah n bulan
  const [y, m] = periode.split('-').map(Number);
  const total = (y * 12 + (m - 1)) + n;
  const ny = Math.floor(total / 12);
  const nm = (total % 12) + 1;
  return `${ny}-${String(nm).padStart(2, '0')}`;
}
function todayStr() {
  return new Date().toISOString().slice(0, 10);
}
function currentPeriode() {
  return new Date().toISOString().slice(0, 7);
}

const DEFAULT_KATEGORI = [
  { nama: 'Iuran Anggota', tipe: 'masuk' },
  { nama: 'Donasi', tipe: 'masuk' },
  { nama: 'Lain-lain (Masuk)', tipe: 'masuk' },
  { nama: 'Konsumsi', tipe: 'keluar' },
  { nama: 'Operasional', tipe: 'keluar' },
  { nama: 'Perlengkapan', tipe: 'keluar' },
  { nama: 'Lain-lain (Keluar)', tipe: 'keluar' },
];

// ============================================================
// MAIN HANDLER
// ============================================================
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === 'OPTIONS') {
      return json({ ok: true });
    }

    if (!path.startsWith('/api/')) {
      // Serve static assets (index.html, manifest.json, sw.js, icon, dll)
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

  // -------------------- AUTH: PUBLIC --------------------
  if (path === '/api/register' && method === 'POST') {
    const body = await request.json();
    const { email, password, nama } = body;
    if (!email || !password || !nama) return err('Email, password, dan nama wajib diisi');
    if (password.length < 6) return err('Password minimal 6 karakter');
    const existing = await env.DB.prepare('SELECT id FROM users WHERE email = ?').bind(email.toLowerCase()).first();
    if (existing) return err('Email sudah terdaftar', 409);
    const { hash, salt } = await hashPassword(password);
    const id = newId();
    await env.DB.prepare(
      'INSERT INTO users (id, email, password_hash, password_salt, nama) VALUES (?, ?, ?, ?, ?)'
    ).bind(id, email.toLowerCase(), hash, salt, nama).run();
    const token = await signJwt({ uid: id, exp: Math.floor(Date.now() / 1000) + 60 * 60 * 24 * 30 }, env.JWT_SECRET);
    return json({ token, user: { id, email: email.toLowerCase(), nama } });
  }

  if (path === '/api/login' && method === 'POST') {
    const body = await request.json();
    const { email, password } = body;
    if (!email || !password) return err('Email dan password wajib diisi');
    const user = await env.DB.prepare('SELECT * FROM users WHERE email = ?').bind(email.toLowerCase()).first();
    if (!user) return err('Email atau password salah', 401);
    const valid = await verifyPassword(password, user.password_hash, user.password_salt);
    if (!valid) return err('Email atau password salah', 401);
    const token = await signJwt({ uid: user.id, exp: Math.floor(Date.now() / 1000) + 60 * 60 * 24 * 30 }, env.JWT_SECRET);
    return json({ token, user: { id: user.id, email: user.email, nama: user.nama } });
  }

  // -------------------- AUTH: PROTECTED --------------------
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
    const valid = await verifyPassword(password_lama, user.password_hash, user.password_salt);
    if (!valid) return err('Password lama salah', 401);
    const { hash, salt } = await hashPassword(password_baru);
    await env.DB.prepare('UPDATE users SET password_hash = ?, password_salt = ? WHERE id = ?').bind(hash, salt, userId).run();
    return json({ ok: true });
  }

  // ---- Organisasi ----
  if (path === '/api/org/list' && method === 'GET') {
    const { results } = await env.DB.prepare(
      `SELECT o.id, o.nama, o.invite_code, o.created_at
       FROM organizations o
       JOIN organization_members m ON m.organization_id = o.id
       WHERE m.user_id = ?
       ORDER BY o.created_at DESC`
    ).bind(userId).all();
    return json({ organizations: results });
  }

  if (path === '/api/org/create' && method === 'POST') {
    const { nama } = await request.json();
    if (!nama) return err('Nama organisasi wajib diisi');
    const orgId = newId();
    let code = inviteCode();
    // pastikan unik
    for (let i = 0; i < 5; i++) {
      const exists = await env.DB.prepare('SELECT 1 FROM organizations WHERE invite_code = ?').bind(code).first();
      if (!exists) break;
      code = inviteCode();
    }
    await env.DB.prepare(
      'INSERT INTO organizations (id, nama, invite_code, created_by) VALUES (?, ?, ?, ?)'
    ).bind(orgId, nama, code, userId).run();
    await env.DB.prepare(
      'INSERT INTO organization_members (id, user_id, organization_id) VALUES (?, ?, ?)'
    ).bind(newId(), userId, orgId).run();
    await env.DB.prepare(
      'INSERT INTO iuran_settings (organization_id, nominal, tanggal_jatuh_tempo) VALUES (?, 0, 10)'
    ).bind(orgId).run();
    for (const k of DEFAULT_KATEGORI) {
      await env.DB.prepare(
        'INSERT INTO kategori (id, organization_id, nama, tipe) VALUES (?, ?, ?, ?)'
      ).bind(newId(), orgId, k.nama, k.tipe).run();
    }
    return json({ organization: { id: orgId, nama, invite_code: code } });
  }

  if (path === '/api/org/join' && method === 'POST') {
    const { invite_code } = await request.json();
    if (!invite_code) return err('Kode undangan wajib diisi');
    const org = await env.DB.prepare('SELECT * FROM organizations WHERE invite_code = ?').bind(invite_code.toUpperCase()).first();
    if (!org) return err('Kode undangan tidak ditemukan', 404);
    const already = await env.DB.prepare(
      'SELECT 1 FROM organization_members WHERE user_id = ? AND organization_id = ?'
    ).bind(userId, org.id).first();
    if (already) return err('Anda sudah tergabung di organisasi ini', 409);
    await env.DB.prepare(
      'INSERT INTO organization_members (id, user_id, organization_id) VALUES (?, ?, ?)'
    ).bind(newId(), userId, org.id).run();
    return json({ organization: { id: org.id, nama: org.nama, invite_code: org.invite_code } });
  }

  // -------------------- SEMUA ENDPOINT DI BAWAH INI BUTUH X-Org-Id --------------------
  const orgCheck = await requireOrgMember(request, env, userId);
  if (orgCheck.error) return orgCheck.error;
  const orgId = orgCheck.orgId;

  // ---- Anggota ----
  if (path === '/api/anggota' && method === 'GET') {
    const { results } = await env.DB.prepare(
      'SELECT * FROM anggota WHERE organization_id = ? ORDER BY nama ASC'
    ).bind(orgId).all();
    return json({ anggota: results });
  }
  if (path === '/api/anggota' && method === 'POST') {
    const { nama, no_hp, catatan } = await request.json();
    if (!nama) return err('Nama anggota wajib diisi');
    const id = newId();
    await env.DB.prepare(
      'INSERT INTO anggota (id, organization_id, nama, no_hp, catatan) VALUES (?, ?, ?, ?, ?)'
    ).bind(id, orgId, nama, no_hp || null, catatan || null).run();
    return json({ id, nama, no_hp, catatan });
  }
  if (path.match(/^\/api\/anggota\/[^/]+$/) && method === 'PUT') {
    const id = path.split('/').pop();
    const { nama, no_hp, catatan, aktif } = await request.json();
    await env.DB.prepare(
      'UPDATE anggota SET nama = ?, no_hp = ?, catatan = ?, aktif = ? WHERE id = ? AND organization_id = ?'
    ).bind(nama, no_hp || null, catatan || null, aktif === undefined ? 1 : (aktif ? 1 : 0), id, orgId).run();
    return json({ ok: true });
  }
  if (path.match(/^\/api\/anggota\/[^/]+$/) && method === 'DELETE') {
    const id = path.split('/').pop();
    await env.DB.prepare('DELETE FROM anggota WHERE id = ? AND organization_id = ?').bind(id, orgId).run();
    return json({ ok: true });
  }

  // ---- Kategori ----
  if (path === '/api/kategori' && method === 'GET') {
    const { results } = await env.DB.prepare(
      'SELECT * FROM kategori WHERE organization_id = ? ORDER BY tipe, nama'
    ).bind(orgId).all();
    return json({ kategori: results });
  }
  if (path === '/api/kategori' && method === 'POST') {
    const { nama, tipe } = await request.json();
    if (!nama || !['masuk', 'keluar'].includes(tipe)) return err('Nama dan tipe (masuk/keluar) wajib diisi');
    const id = newId();
    await env.DB.prepare(
      'INSERT INTO kategori (id, organization_id, nama, tipe) VALUES (?, ?, ?, ?)'
    ).bind(id, orgId, nama, tipe).run();
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
    const { nominal, tanggal_jatuh_tempo } = await request.json();
    if (nominal === undefined || nominal < 0) return err('Nominal iuran tidak valid');
    if (!tanggal_jatuh_tempo || tanggal_jatuh_tempo < 1 || tanggal_jatuh_tempo > 28) return err('Tanggal jatuh tempo harus 1-28');
    await env.DB.prepare(
      'UPDATE iuran_settings SET nominal = ?, tanggal_jatuh_tempo = ?, updated_at = datetime("now") WHERE organization_id = ?'
    ).bind(nominal, tanggal_jatuh_tempo, orgId).run();
    return json({ ok: true });
  }

  // ---- Transaksi umum ----
  if (path === '/api/transaksi' && method === 'GET') {
    const from = url.searchParams.get('from');
    const to = url.searchParams.get('to');
    let query = `SELECT t.*, a.nama as anggota_nama FROM transaksi t
                 LEFT JOIN anggota a ON a.id = t.anggota_id
                 WHERE t.organization_id = ?`;
    const params = [orgId];
    if (from) { query += ' AND t.tanggal >= ?'; params.push(from); }
    if (to) { query += ' AND t.tanggal <= ?'; params.push(to); }
    query += ' ORDER BY t.tanggal DESC, t.created_at DESC LIMIT 500';
    const { results } = await env.DB.prepare(query).bind(...params).all();
    return json({ transaksi: results });
  }

  if (path === '/api/transaksi' && method === 'POST') {
    const { tipe, kategori, jumlah, catatan, tanggal } = await request.json();
    if (!['masuk', 'keluar'].includes(tipe)) return err('Tipe transaksi harus masuk/keluar');
    if (!jumlah || jumlah <= 0) return err('Jumlah harus lebih dari 0');
    const id = newId();
    const tgl = tanggal || todayStr();
    await env.DB.prepare(
      `INSERT INTO transaksi (id, organization_id, tipe, sumber, kategori, jumlah, catatan, tanggal, created_by)
       VALUES (?, ?, ?, 'umum', ?, ?, ?, ?, ?)`
    ).bind(id, orgId, tipe, kategori || null, jumlah, catatan || null, tgl, userId).run();
    const org = await env.DB.prepare('SELECT nama FROM organizations WHERE id = ?').bind(orgId).first();
    return json({
      transaksi: { id, tipe, kategori, jumlah, catatan, tanggal: tgl, organisasi: org.nama },
    });
  }

  if (path.match(/^\/api\/transaksi\/[^/]+$/) && method === 'DELETE') {
    const id = path.split('/').pop();
    await env.DB.prepare('DELETE FROM transaksi WHERE id = ? AND organization_id = ?').bind(id, orgId).run();
    return json({ ok: true });
  }

  if (path.match(/^\/api\/transaksi\/[^/]+\/struk$/) && method === 'GET') {
    const id = path.split('/')[3];
    const t = await env.DB.prepare(
      `SELECT t.*, a.nama as anggota_nama FROM transaksi t
       LEFT JOIN anggota a ON a.id = t.anggota_id
       WHERE t.id = ? AND t.organization_id = ?`
    ).bind(id, orgId).first();
    if (!t) return err('Transaksi tidak ditemukan', 404);
    const org = await env.DB.prepare('SELECT nama FROM organizations WHERE id = ?').bind(orgId).first();
    let periodeList = [];
    if (t.sumber === 'iuran') {
      const pr = await env.DB.prepare(
        'SELECT periode FROM pembayaran_iuran WHERE transaksi_id = ? ORDER BY periode'
      ).bind(id).all();
      periodeList = pr.results.map((r) => r.periode);
    }
    return json({ struk: { ...t, organisasi: org.nama, periode_list: periodeList } });
  }

  // ---- Iuran: status per periode ----
  if (path === '/api/iuran/status' && method === 'GET') {
    const periode = url.searchParams.get('periode') || currentPeriode();
    const { results: semuaAnggota } = await env.DB.prepare(
      'SELECT id, nama, no_hp FROM anggota WHERE organization_id = ? AND aktif = 1 ORDER BY nama'
    ).bind(orgId).all();
    const { results: bayar } = await env.DB.prepare(
      'SELECT anggota_id, jumlah_dibayar, tanggal_bayar FROM pembayaran_iuran WHERE organization_id = ? AND periode = ?'
    ).bind(orgId, periode).all();
    const bayarMap = {};
    bayar.forEach((b) => { bayarMap[b.anggota_id] = b; });
    const status = semuaAnggota.map((a) => ({
      anggota_id: a.id,
      nama: a.nama,
      no_hp: a.no_hp,
      status: bayarMap[a.id] ? 'lunas' : 'menunggak',
      jumlah_dibayar: bayarMap[a.id]?.jumlah_dibayar || 0,
      tanggal_bayar: bayarMap[a.id]?.tanggal_bayar || null,
    }));
    const lunas = status.filter((s) => s.status === 'lunas').length;
    return json({
      periode,
      total_anggota: status.length,
      lunas,
      menunggak: status.length - lunas,
      status,
    });
  }

  // ---- Iuran: bayar (1 bulan atau sekaligus banyak bulan / setahun) ----
  if (path === '/api/iuran/bayar' && method === 'POST') {
    const { anggota_id, periode_mulai, jumlah_periode, catatan, tanggal } = await request.json();
    if (!anggota_id) return err('Anggota wajib dipilih');
    const jp = parseInt(jumlah_periode) || 1;
    if (jp < 1 || jp > 24) return err('Jumlah periode tidak valid (1-24 bulan)');
    const periodeAwal = periode_mulai || currentPeriode();

    const anggota = await env.DB.prepare('SELECT * FROM anggota WHERE id = ? AND organization_id = ?').bind(anggota_id, orgId).first();
    if (!anggota) return err('Anggota tidak ditemukan', 404);
    const settings = await env.DB.prepare('SELECT * FROM iuran_settings WHERE organization_id = ?').bind(orgId).first();
    if (!settings || settings.nominal <= 0) return err('Nominal iuran belum diatur. Atur di menu Pengaturan Iuran.');

    const periodeList = [];
    for (let i = 0; i < jp; i++) periodeList.push(periodAdd(periodeAwal, i));

    // Cek apakah ada periode yang sudah lunas
    const placeholders = periodeList.map(() => '?').join(',');
    const { results: sudahBayar } = await env.DB.prepare(
      `SELECT periode FROM pembayaran_iuran WHERE anggota_id = ? AND periode IN (${placeholders})`
    ).bind(anggota_id, ...periodeList).all();
    if (sudahBayar.length > 0) {
      return err(`Periode ${sudahBayar.map((s) => s.periode).join(', ')} sudah lunas untuk anggota ini`, 409);
    }

    const jumlahTotal = settings.nominal * jp;
    const tgl = tanggal || todayStr();
    const transaksiId = newId();
    await env.DB.prepare(
      `INSERT INTO transaksi (id, organization_id, tipe, sumber, kategori, jumlah, catatan, anggota_id, tanggal, created_by)
       VALUES (?, ?, 'masuk', 'iuran', 'Iuran Anggota', ?, ?, ?, ?, ?)`
    ).bind(transaksiId, orgId, jumlahTotal, catatan || `Iuran ${jp} bulan (${anggota.nama})`, anggota_id, tgl, userId).run();

    for (const p of periodeList) {
      await env.DB.prepare(
        `INSERT INTO pembayaran_iuran (id, organization_id, anggota_id, periode, jumlah_dibayar, transaksi_id, tanggal_bayar)
         VALUES (?, ?, ?, ?, ?, ?, ?)`
      ).bind(newId(), orgId, anggota_id, p, settings.nominal, transaksiId, tgl).run();
    }

    const org = await env.DB.prepare('SELECT nama FROM organizations WHERE id = ?').bind(orgId).first();
    return json({
      transaksi: {
        id: transaksiId, anggota: anggota.nama, jumlah: jumlahTotal,
        periode_list: periodeList, tanggal: tgl, organisasi: org.nama,
      },
    });
  }

  // ---- Laporan bulanan ----
  if (path === '/api/laporan/bulanan' && method === 'GET') {
    const bulan = url.searchParams.get('bulan') || currentPeriode(); // YYYY-MM
    const { results: trx } = await env.DB.prepare(
      `SELECT * FROM transaksi WHERE organization_id = ? AND substr(tanggal,1,7) = ? ORDER BY tanggal`
    ).bind(orgId, bulan).all();
    const totalMasuk = trx.filter((t) => t.tipe === 'masuk').reduce((a, t) => a + t.jumlah, 0);
    const totalKeluar = trx.filter((t) => t.tipe === 'keluar').reduce((a, t) => a + t.jumlah, 0);
    const perKategori = {};
    trx.forEach((t) => {
      const k = t.kategori || 'Lainnya';
      if (!perKategori[k]) perKategori[k] = { masuk: 0, keluar: 0 };
      perKategori[k][t.tipe] += t.jumlah;
    });
    const { results: iuranStatus } = await env.DB.prepare(
      'SELECT COUNT(DISTINCT anggota_id) as jumlah FROM pembayaran_iuran WHERE organization_id = ? AND periode = ?'
    ).bind(orgId, bulan).all();
    const totalAnggota = await env.DB.prepare('SELECT COUNT(*) as c FROM anggota WHERE organization_id = ? AND aktif = 1').bind(orgId).first();
    return json({
      bulan,
      total_masuk: totalMasuk,
      total_keluar: totalKeluar,
      saldo_bersih: totalMasuk - totalKeluar,
      jumlah_transaksi: trx.length,
      per_kategori: perKategori,
      iuran_lunas: iuranStatus[0]?.jumlah || 0,
      iuran_menunggak: (totalAnggota.c || 0) - (iuranStatus[0]?.jumlah || 0),
      total_anggota: totalAnggota.c || 0,
      transaksi: trx,
    });
  }

  // ---- Laporan tahunan ----
  if (path === '/api/laporan/tahunan' && method === 'GET') {
    const tahun = url.searchParams.get('tahun') || String(new Date().getFullYear());
    const { results: trx } = await env.DB.prepare(
      `SELECT * FROM transaksi WHERE organization_id = ? AND substr(tanggal,1,4) = ? ORDER BY tanggal`
    ).bind(orgId, tahun).all();
    const perBulan = {};
    for (let i = 1; i <= 12; i++) {
      const key = `${tahun}-${String(i).padStart(2, '0')}`;
      perBulan[key] = { masuk: 0, keluar: 0 };
    }
    trx.forEach((t) => {
      const key = t.tanggal.slice(0, 7);
      if (!perBulan[key]) perBulan[key] = { masuk: 0, keluar: 0 };
      perBulan[key][t.tipe] += t.jumlah;
    });
    const totalMasuk = trx.filter((t) => t.tipe === 'masuk').reduce((a, t) => a + t.jumlah, 0);
    const totalKeluar = trx.filter((t) => t.tipe === 'keluar').reduce((a, t) => a + t.jumlah, 0);
    return json({
      tahun,
      total_masuk: totalMasuk,
      total_keluar: totalKeluar,
      saldo_bersih: totalMasuk - totalKeluar,
      jumlah_transaksi: trx.length,
      per_bulan: perBulan,
    });
  }

  // ---- Laporan tunggakan (khusus) ----
  if (path === '/api/laporan/tunggakan' && method === 'GET') {
    const periode = url.searchParams.get('periode') || currentPeriode();
    const { results: semuaAnggota } = await env.DB.prepare(
      'SELECT id, nama, no_hp FROM anggota WHERE organization_id = ? AND aktif = 1 ORDER BY nama'
    ).bind(orgId).all();
    const { results: bayar } = await env.DB.prepare(
      'SELECT anggota_id FROM pembayaran_iuran WHERE organization_id = ? AND periode = ?'
    ).bind(orgId, periode).all();
    const sudahBayarSet = new Set(bayar.map((b) => b.anggota_id));
    const menunggak = semuaAnggota.filter((a) => !sudahBayarSet.has(a.id));
    return json({ periode, menunggak, jumlah: menunggak.length });
  }

  return err('Endpoint tidak ditemukan', 404);
}
