// ============================================================
// KAS KITA — Cloudflare Worker (Redesign v3)
// Backend tunggal: auth, organisasi, anggota, transaksi, iuran,
// laporan, profil, notifikasi.
// Binding yang dibutuhkan (wrangler.toml):
//   DB           -> D1 Database
//   R2_FOTO      -> R2 Bucket (foto profil)
//   JWT_SECRET   -> secret, untuk sign token
//   MAILCHANNELS_API_KEY -> secret, MailChannels Email API baru
//   MAIL_FROM    -> var, alamat pengirim terverifikasi
//   APP_URL      -> var, base URL frontend (untuk link reset password)
// ============================================================

// ---------- Helper: Response ----------
function json(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Org-Id',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
      ...extraHeaders,
    },
  });
}

function errorResponse(message, status = 400, code = null) {
  return json({ error: true, message, code }, status);
}

// ---------- Helper: ID & Kode ----------
function newId() {
  return crypto.randomUUID();
}

function slugify(text) {
  return text
    .toString()
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '')
    .slice(0, 20);
}

async function generateKodeOrganisasi(db, namaOrganisasi) {
  const slug = slugify(namaOrganisasi) || 'ORG';
  for (let attempt = 0; attempt < 50; attempt++) {
    const nomor = String(attempt + 1).padStart(3, '0');
    const kode = `KITA-${slug}-${nomor}`;
    const existing = await db
      .prepare('SELECT id FROM organizations WHERE kode_id = ?')
      .bind(kode)
      .first();
    if (!existing) return kode;
  }
  // fallback kalau 50 percobaan habis (sangat tidak mungkin)
  return `KITA-${slug}-${Date.now().toString().slice(-6)}`;
}

async function generateNoReferensi(db, tanggalISO) {
  const tgl = tanggalISO.replace(/-/g, '').slice(0, 8); // YYYYMMDD
  const prefix = `TRX-${tgl}-`;
  const row = await db
    .prepare(
      `SELECT no_referensi FROM transaksi
       WHERE no_referensi LIKE ? ORDER BY no_referensi DESC LIMIT 1`
    )
    .bind(prefix + '%')
    .first();
  let urut = 1;
  if (row && row.no_referensi) {
    const lastUrut = parseInt(row.no_referensi.slice(-4), 10);
    if (!isNaN(lastUrut)) urut = lastUrut + 1;
  }
  return prefix + String(urut).padStart(4, '0');
}

// ---------- Helper: Password hashing (PBKDF2 via Web Crypto) ----------
async function hashPassword(password) {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(password),
    'PBKDF2',
    false,
    ['deriveBits']
  );
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt, iterations: 100000, hash: 'SHA-256' },
    key,
    256
  );
  const hashHex = [...new Uint8Array(bits)].map((b) => b.toString(16).padStart(2, '0')).join('');
  const saltHex = [...salt].map((b) => b.toString(16).padStart(2, '0')).join('');
  return `pbkdf2$100000$${saltHex}$${hashHex}`;
}

async function verifyPassword(password, stored) {
  if (!stored) return false;
  const parts = stored.split('$');
  if (parts.length !== 4 || parts[0] !== 'pbkdf2') return false;
  const iterations = parseInt(parts[1], 10);
  const salt = new Uint8Array(parts[2].match(/.{1,2}/g).map((b) => parseInt(b, 16)));
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(password),
    'PBKDF2',
    false,
    ['deriveBits']
  );
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt, iterations, hash: 'SHA-256' },
    key,
    256
  );
  const hashHex = [...new Uint8Array(bits)].map((b) => b.toString(16).padStart(2, '0')).join('');
  return hashHex === parts[3];
}

// ---------- Helper: JWT (HS256, tanpa library) ----------
function base64UrlEncode(bytes) {
  let str = btoa(String.fromCharCode(...bytes));
  return str.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function base64UrlDecode(str) {
  str = str.replace(/-/g, '+').replace(/_/g, '/');
  while (str.length % 4) str += '=';
  const bin = atob(str);
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

async function signJWT(payload, secret, expiresInSeconds = 60 * 60 * 24 * 30) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const fullPayload = { ...payload, iat: now, exp: now + expiresInSeconds };
  const encHeader = base64UrlEncode(new TextEncoder().encode(JSON.stringify(header)));
  const encPayload = base64UrlEncode(new TextEncoder().encode(JSON.stringify(fullPayload)));
  const data = `${encHeader}.${encPayload}`;
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const sig = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(data));
  const encSig = base64UrlEncode(new Uint8Array(sig));
  return `${data}.${encSig}`;
}

async function verifyJWT(token, secret) {
  try {
    const [encHeader, encPayload, encSig] = token.split('.');
    if (!encHeader || !encPayload || !encSig) return null;
    const data = `${encHeader}.${encPayload}`;
    const key = await crypto.subtle.importKey(
      'raw',
      new TextEncoder().encode(secret),
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['verify']
    );
    const sigBytes = base64UrlDecode(encSig);
    const valid = await crypto.subtle.verify(
      'HMAC',
      key,
      sigBytes,
      new TextEncoder().encode(data)
    );
    if (!valid) return null;
    const payload = JSON.parse(new TextDecoder().decode(base64UrlDecode(encPayload)));
    if (payload.exp && payload.exp < Math.floor(Date.now() / 1000)) return null;
    return payload;
  } catch (e) {
    return null;
  }
}

// ---------- Helper: Auth Context ----------
async function getAuthUser(request, env) {
  const authHeader = request.headers.get('Authorization') || '';
  const match = authHeader.match(/^Bearer (.+)$/);
  if (!match) return null;
  const payload = await verifyJWT(match[1], env.JWT_SECRET);
  if (!payload || !payload.sub) return null;
  const user = await env.DB.prepare('SELECT * FROM users WHERE id = ?').bind(payload.sub).first();
  return user || null;
}

// Pastikan user adalah anggota organisasi yang diklaim di header X-Org-Id.
// Mengembalikan organization_id yang TERVALIDASI, bukan sekadar header mentah.
async function getActiveOrgId(request, env, userId) {
  const orgId = request.headers.get('X-Org-Id');
  if (!orgId) return { error: 'X-Org-Id header wajib diisi' };
  const membership = await env.DB.prepare(
    'SELECT * FROM organization_members WHERE organization_id = ? AND user_id = ?'
  )
    .bind(orgId, userId)
    .first();
  if (!membership) return { error: 'Anda bukan anggota organisasi ini' };
  return { orgId, membership };
}

// ---------- Helper: Email via MailChannels Email API (baru) ----------
async function sendEmail(env, { to, subject, html }) {
  const res = await fetch('https://api.mailchannels.net/tx/v1/send', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Api-Key': env.MAILCHANNELS_API_KEY,
    },
    body: JSON.stringify({
      personalizations: [{ to: [{ email: to }] }],
      from: { email: env.MAIL_FROM, name: 'Kas Kita' },
      subject,
      content: [{ type: 'text/html', value: html }],
    }),
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`Gagal mengirim email (status ${res.status}): ${text}`);
  }
  return true;
}

// ============================================================
// AUTH ROUTES
// ============================================================
async function handleAuth(request, env, path) {
  // POST /api/auth/register
  if (path === '/api/auth/register' && request.method === 'POST') {
    const body = await request.json().catch(() => ({}));
    const { nama, email, password, no_hp } = body;
    if (!nama || !email || !password) {
      return errorResponse('Nama, email, dan password wajib diisi');
    }
    if (password.length < 6) {
      return errorResponse('Password minimal 6 karakter');
    }
    const existing = await env.DB.prepare('SELECT id FROM users WHERE email = ?')
      .bind(email.toLowerCase())
      .first();
    if (existing) return errorResponse('Email sudah terdaftar', 409);

    const id = newId();
    const passwordHash = await hashPassword(password);
    await env.DB.prepare(
      `INSERT INTO users (id, nama, email, password_hash, no_hp) VALUES (?, ?, ?, ?, ?)`
    )
      .bind(id, nama, email.toLowerCase(), passwordHash, no_hp || null)
      .run();

    const token = await signJWT({ sub: id }, env.JWT_SECRET);
    return json({ token, user: { id, nama, email: email.toLowerCase() } }, 201);
  }

  // POST /api/auth/login
  if (path === '/api/auth/login' && request.method === 'POST') {
    const body = await request.json().catch(() => ({}));
    const { email, password, ingat_saya } = body;
    if (!email || !password) return errorResponse('Email dan password wajib diisi');

    const user = await env.DB.prepare('SELECT * FROM users WHERE email = ?')
      .bind(email.toLowerCase())
      .first();
    if (!user) return errorResponse('Email atau password salah', 401);

    const valid = await verifyPassword(password, user.password_hash);
    if (!valid) return errorResponse('Email atau password salah', 401);

    const expiresIn = ingat_saya ? 60 * 60 * 24 * 90 : 60 * 60 * 24 * 7;
    const token = await signJWT({ sub: user.id }, env.JWT_SECRET, expiresIn);
    return json({
      token,
      user: { id: user.id, nama: user.nama, email: user.email, foto_url: user.foto_url },
    });
  }

  // POST /api/auth/google  (menerima id_token dari Google Sign-In, diverifikasi via tokeninfo)
  if (path === '/api/auth/google' && request.method === 'POST') {
    const body = await request.json().catch(() => ({}));
    const { id_token } = body;
    if (!id_token) return errorResponse('id_token wajib diisi');

    const verifyRes = await fetch(
      `https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(id_token)}`
    );
    if (!verifyRes.ok) return errorResponse('Token Google tidak valid', 401);
    const payload = await verifyRes.json();
    const googleId = payload.sub;
    const email = (payload.email || '').toLowerCase();
    const nama = payload.name || email.split('@')[0];

    if (!googleId || !email) return errorResponse('Token Google tidak valid', 401);

    let user = await env.DB.prepare('SELECT * FROM users WHERE google_id = ? OR email = ?')
      .bind(googleId, email)
      .first();

    if (!user) {
      const id = newId();
      await env.DB.prepare(
        `INSERT INTO users (id, nama, email, google_id, foto_url) VALUES (?, ?, ?, ?, ?)`
      )
        .bind(id, nama, email, googleId, payload.picture || null)
        .run();
      user = { id, nama, email };
    } else if (!user.google_id) {
      // akun lama daftar manual, sekarang login pakai Google dengan email sama -> tautkan
      await env.DB.prepare('UPDATE users SET google_id = ? WHERE id = ?')
        .bind(googleId, user.id)
        .run();
    }

    const token = await signJWT({ sub: user.id }, env.JWT_SECRET);
    return json({ token, user: { id: user.id, nama: user.nama, email: user.email } });
  }

  // POST /api/auth/forgot-password
  if (path === '/api/auth/forgot-password' && request.method === 'POST') {
    const body = await request.json().catch(() => ({}));
    const { email } = body;
    if (!email) return errorResponse('Email wajib diisi');

    const user = await env.DB.prepare('SELECT * FROM users WHERE email = ?')
      .bind(email.toLowerCase())
      .first();

    // Selalu balas sukses walau email tidak ditemukan (hindari enumerasi akun)
    if (!user) return json({ message: 'Kalau email terdaftar, link reset sudah dikirim' });

    const rawToken = base64UrlEncode(crypto.getRandomValues(new Uint8Array(32)));
    const tokenHash = await hashPassword(rawToken); // reuse PBKDF2 sebagai hash token
    const id = newId();
    const expiresAt = new Date(Date.now() + 60 * 60 * 1000).toISOString(); // 1 jam

    await env.DB.prepare(
      `INSERT INTO password_resets (id, user_id, token_hash, expires_at) VALUES (?, ?, ?, ?)`
    )
      .bind(id, user.id, tokenHash, expiresAt)
      .run();

    const resetLink = `${env.APP_URL}/reset-password?token=${rawToken}&uid=${user.id}`;
    try {
      await sendEmail(env, {
        to: user.email,
        subject: 'Reset Password Kas Kita',
        html: `<p>Halo ${user.nama},</p><p>Klik link berikut untuk reset password (berlaku 1 jam):</p><p><a href="${resetLink}">${resetLink}</a></p><p>Kalau Anda tidak meminta ini, abaikan email ini.</p>`,
      });
    } catch (e) {
      return errorResponse('Gagal mengirim email reset. Coba lagi nanti.', 502);
    }

    return json({ message: 'Kalau email terdaftar, link reset sudah dikirim' });
  }

  // POST /api/auth/reset-password
  if (path === '/api/auth/reset-password' && request.method === 'POST') {
    const body = await request.json().catch(() => ({}));
    const { uid, token, password_baru } = body;
    if (!uid || !token || !password_baru) return errorResponse('Data tidak lengkap');
    if (password_baru.length < 6) return errorResponse('Password minimal 6 karakter');

    const resets = await env.DB.prepare(
      `SELECT * FROM password_resets WHERE user_id = ? AND used = 0 AND expires_at > datetime('now') ORDER BY created_at DESC`
    )
      .bind(uid)
      .all();

    let matched = null;
    for (const row of resets.results || []) {
      if (await verifyPassword(token, row.token_hash)) {
        matched = row;
        break;
      }
    }
    if (!matched) return errorResponse('Token reset tidak valid atau sudah kedaluwarsa', 400);

    const newHash = await hashPassword(password_baru);
    await env.DB.prepare('UPDATE users SET password_hash = ?, updated_at = datetime("now") WHERE id = ?')
      .bind(newHash, uid)
      .run();
    await env.DB.prepare('UPDATE password_resets SET used = 1 WHERE id = ?').bind(matched.id).run();

    return json({ message: 'Password berhasil diubah' });
  }

  return errorResponse('Endpoint auth tidak ditemukan', 404);
}

// ============================================================
// ORGANIZATIONS ROUTES
// ============================================================
async function handleOrganizations(request, env, path, user) {
  // GET /api/organizations  -> daftar organisasi milik user (untuk pilih saat login/switch)
  if (path === '/api/organizations' && request.method === 'GET') {
    const rows = await env.DB.prepare(
      `SELECT o.id, o.nama, o.kode_id, m.jabatan
       FROM organizations o
       JOIN organization_members m ON m.organization_id = o.id
       WHERE m.user_id = ?
       ORDER BY o.nama`
    )
      .bind(user.id)
      .all();
    return json({ organizations: rows.results || [] });
  }

  // POST /api/organizations  (buat organisasi baru)
  if (path === '/api/organizations' && request.method === 'POST') {
    const body = await request.json().catch(() => ({}));
    const { nama } = body;
    if (!nama || !nama.trim()) return errorResponse('Nama organisasi wajib diisi');

    const id = newId();
    const kodeId = await generateKodeOrganisasi(env.DB, nama.trim());
    const now = new Date().toISOString();

    await env.DB.batch([
      env.DB.prepare(
        `INSERT INTO organizations (id, nama, kode_id, created_by) VALUES (?, ?, ?, ?)`
      ).bind(id, nama.trim(), kodeId, user.id),
      env.DB.prepare(
        `INSERT INTO organization_members (id, organization_id, user_id, jabatan) VALUES (?, ?, ?, ?)`
      ).bind(newId(), id, user.id, 'Ketua Organisasi'),
      env.DB.prepare(
        `INSERT INTO iuran_settings (organization_id, nominal_bulanan, tanggal_mulai_organisasi, updated_by)
         VALUES (?, 0, ?, ?)`
      ).bind(id, now.slice(0, 10), user.id),
    ]);

    // Seed kategori default (pengeluaran)
    const defaultKategori = ['Konsumsi Kegiatan', 'Transportasi', 'Perlengkapan', 'Lainnya'];
    const seedStmts = defaultKategori.map((namaKategori) =>
      env.DB.prepare(
        `INSERT INTO kategori (id, organization_id, nama, tipe) VALUES (?, ?, ?, 'pengeluaran')`
      ).bind(newId(), id, namaKategori)
    );
    await env.DB.batch(seedStmts);

    // Seed akun default: Kas Tunai
    await env.DB.prepare(
      `INSERT INTO akun (id, organization_id, nama, jenis, saldo_awal) VALUES (?, ?, 'Kas Tunai', 'tunai', 0)`
    )
      .bind(newId(), id)
      .run();

    return json({ organization: { id, nama: nama.trim(), kode_id: kodeId } }, 201);
  }

  // POST /api/organizations/gabung  (join pakai kode_id)
  if (path === '/api/organizations/gabung' && request.method === 'POST') {
    const body = await request.json().catch(() => ({}));
    const { kode_id } = body;
    if (!kode_id || !kode_id.trim()) return errorResponse('Kode organisasi wajib diisi');

    const org = await env.DB.prepare('SELECT * FROM organizations WHERE kode_id = ?')
      .bind(kode_id.trim().toUpperCase())
      .first();
    if (!org) return errorResponse('Kode organisasi tidak ditemukan', 404);

    const existing = await env.DB.prepare(
      'SELECT id FROM organization_members WHERE organization_id = ? AND user_id = ?'
    )
      .bind(org.id, user.id)
      .first();
    if (existing) return errorResponse('Anda sudah tergabung di organisasi ini', 409);

    await env.DB.prepare(
      `INSERT INTO organization_members (id, organization_id, user_id, jabatan) VALUES (?, ?, ?, 'Anggota')`
    )
      .bind(newId(), org.id, user.id)
      .run();

    return json({ organization: { id: org.id, nama: org.nama, kode_id: org.kode_id } }, 201);
  }

  return errorResponse('Endpoint organizations tidak ditemukan', 404);
}

// ============================================================
// PROFIL ROUTES (butuh user, tidak semuanya butuh org aktif)
// ============================================================
async function handleProfil(request, env, path, user, orgCtx) {
  // GET /api/profil
  if (path === '/api/profil' && request.method === 'GET') {
    let jabatan = null;
    if (orgCtx && orgCtx.orgId) {
      jabatan = orgCtx.membership.jabatan;
    }
    return json({
      user: {
        id: user.id,
        nama: user.nama,
        email: user.email,
        no_hp: user.no_hp,
        foto_url: user.foto_url,
        tema: user.tema,
        created_at: user.created_at,
      },
      jabatan,
    });
  }

  // PUT /api/profil  (edit nama / no_hp / tema)
  if (path === '/api/profil' && request.method === 'PUT') {
    const body = await request.json().catch(() => ({}));
    const fields = [];
    const values = [];
    if (typeof body.nama === 'string' && body.nama.trim()) {
      fields.push('nama = ?');
      values.push(body.nama.trim());
    }
    if (typeof body.no_hp === 'string') {
      fields.push('no_hp = ?');
      values.push(body.no_hp);
    }
    if (body.tema === 'terang' || body.tema === 'gelap') {
      fields.push('tema = ?');
      values.push(body.tema);
    }
    if (fields.length === 0) return errorResponse('Tidak ada data untuk diubah');
    fields.push('updated_at = datetime("now")');
    values.push(user.id);
    await env.DB.prepare(`UPDATE users SET ${fields.join(', ')} WHERE id = ?`).bind(...values).run();
    return json({ message: 'Profil berhasil diperbarui' });
  }

  // PUT /api/profil/jabatan  (label tampilan saja, khusus org aktif)
  if (path === '/api/profil/jabatan' && request.method === 'PUT') {
    if (!orgCtx || !orgCtx.orgId) return errorResponse('Organisasi aktif tidak ditemukan', 400);
    const body = await request.json().catch(() => ({}));
    if (!body.jabatan || !body.jabatan.trim()) return errorResponse('Jabatan wajib diisi');
    await env.DB.prepare(
      'UPDATE organization_members SET jabatan = ? WHERE organization_id = ? AND user_id = ?'
    )
      .bind(body.jabatan.trim(), orgCtx.orgId, user.id)
      .run();
    return json({ message: 'Jabatan berhasil diperbarui' });
  }

  // PUT /api/profil/email
  if (path === '/api/profil/email' && request.method === 'PUT') {
    const body = await request.json().catch(() => ({}));
    const { email_baru, password } = body;
    if (!email_baru || !password) return errorResponse('Email baru dan password wajib diisi');
    const valid = await verifyPassword(password, user.password_hash);
    if (!valid) return errorResponse('Password salah', 401);
    const existing = await env.DB.prepare('SELECT id FROM users WHERE email = ? AND id != ?')
      .bind(email_baru.toLowerCase(), user.id)
      .first();
    if (existing) return errorResponse('Email sudah dipakai akun lain', 409);
    await env.DB.prepare('UPDATE users SET email = ?, updated_at = datetime("now") WHERE id = ?')
      .bind(email_baru.toLowerCase(), user.id)
      .run();
    return json({ message: 'Email berhasil diubah' });
  }

  // PUT /api/profil/password
  if (path === '/api/profil/password' && request.method === 'PUT') {
    const body = await request.json().catch(() => ({}));
    const { password_lama, password_baru } = body;
    if (!password_lama || !password_baru) return errorResponse('Data tidak lengkap');
    if (password_baru.length < 6) return errorResponse('Password baru minimal 6 karakter');
    const valid = await verifyPassword(password_lama, user.password_hash);
    if (!valid) return errorResponse('Password lama salah', 401);
    const newHash = await hashPassword(password_baru);
    await env.DB.prepare('UPDATE users SET password_hash = ?, updated_at = datetime("now") WHERE id = ?')
      .bind(newHash, user.id)
      .run();
    return json({ message: 'Password berhasil diubah' });
  }

  // POST /api/profil/foto  (upload ke R2, expects multipart atau base64 JSON)
  if (path === '/api/profil/foto' && request.method === 'POST') {
    const contentType = request.headers.get('Content-Type') || '';
    if (!contentType.includes('application/json')) {
      return errorResponse('Gunakan JSON dengan field foto_base64 dan mime_type');
    }
    const body = await request.json().catch(() => ({}));
    const { foto_base64, mime_type } = body;
    if (!foto_base64 || !mime_type) return errorResponse('foto_base64 dan mime_type wajib diisi');
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(mime_type)) {
      return errorResponse('Format foto harus JPEG, PNG, atau WEBP');
    }
    const bytes = Uint8Array.from(atob(foto_base64), (c) => c.charCodeAt(0));
    if (bytes.length > 5 * 1024 * 1024) return errorResponse('Ukuran foto maksimal 5MB');

    const ext = mime_type.split('/')[1];
    const key = `profil/${user.id}-${Date.now()}.${ext}`;
    await env.R2_FOTO.put(key, bytes, { httpMetadata: { contentType: mime_type } });

    const fotoUrl = `${env.APP_URL}/r2/${key}`;
    await env.DB.prepare('UPDATE users SET foto_url = ?, updated_at = datetime("now") WHERE id = ?')
      .bind(fotoUrl, user.id)
      .run();

    return json({ foto_url: fotoUrl });
  }

  return errorResponse('Endpoint profil tidak ditemukan', 404);
}

// ============================================================
// AKUN (KAS) ROUTES — butuh org aktif
// ============================================================
async function handleAkun(request, env, path, orgId, user) {
  // GET /api/akun  -> list akun + saldo terhitung otomatis
  if (path === '/api/akun' && request.method === 'GET') {
    const akunList = await env.DB.prepare(
      'SELECT * FROM akun WHERE organization_id = ? ORDER BY created_at'
    )
      .bind(orgId)
      .all();

    const hasil = [];
    let totalSaldo = 0;
    for (const akun of akunList.results || []) {
      const saldo = await hitungSaldoAkun(env.DB, akun);
      hasil.push({ ...akun, saldo });
      totalSaldo += saldo;
    }
    return json({ akun: hasil, total_saldo: totalSaldo });
  }

  // POST /api/akun
  if (path === '/api/akun' && request.method === 'POST') {
    const body = await request.json().catch(() => ({}));
    const { nama, jenis, nomor_rekening, saldo_awal } = body;
    if (!nama || !jenis) return errorResponse('Nama dan jenis akun wajib diisi');
    if (!['tunai', 'bank', 'e_wallet'].includes(jenis)) return errorResponse('Jenis akun tidak valid');

    const id = newId();
    await env.DB.prepare(
      `INSERT INTO akun (id, organization_id, nama, jenis, nomor_rekening, saldo_awal)
       VALUES (?, ?, ?, ?, ?, ?)`
    )
      .bind(id, orgId, nama.trim(), jenis, nomor_rekening || null, saldo_awal || 0)
      .run();
    return json({ id }, 201);
  }

  // PUT /api/akun/:id
  const putMatch = path.match(/^\/api\/akun\/([^/]+)$/);
  if (putMatch && request.method === 'PUT') {
    const akunId = putMatch[1];
    const akun = await env.DB.prepare('SELECT * FROM akun WHERE id = ? AND organization_id = ?')
      .bind(akunId, orgId)
      .first();
    if (!akun) return errorResponse('Akun tidak ditemukan', 404);

    const body = await request.json().catch(() => ({}));
    const fields = [];
    const values = [];
    if (typeof body.nama === 'string' && body.nama.trim()) {
      fields.push('nama = ?');
      values.push(body.nama.trim());
    }
    if (typeof body.nomor_rekening === 'string') {
      fields.push('nomor_rekening = ?');
      values.push(body.nomor_rekening);
    }
    if (typeof body.nonaktif === 'boolean') {
      fields.push('nonaktif = ?');
      values.push(body.nonaktif ? 1 : 0);
    }
    if (fields.length === 0) return errorResponse('Tidak ada data untuk diubah');
    fields.push('updated_at = datetime("now")');
    values.push(akunId, orgId);
    await env.DB.prepare(
      `UPDATE akun SET ${fields.join(', ')} WHERE id = ? AND organization_id = ?`
    ).bind(...values).run();
    return json({ message: 'Akun berhasil diperbarui' });
  }

  // DELETE /api/akun/:id  (hanya boleh kalau belum pernah dipakai transaksi)
  const delMatch = path.match(/^\/api\/akun\/([^/]+)$/);
  if (delMatch && request.method === 'DELETE') {
    const akunId = delMatch[1];
    const dipakai = await env.DB.prepare(
      `SELECT id FROM transaksi WHERE organization_id = ? AND
       (akun_id = ? OR akun_asal_id = ? OR akun_tujuan_id = ?) LIMIT 1`
    )
      .bind(orgId, akunId, akunId, akunId)
      .first();
    if (dipakai) {
      return errorResponse(
        'Akun ini sudah punya riwayat transaksi, tidak bisa dihapus. Nonaktifkan saja.',
        409
      );
    }
    await env.DB.prepare('DELETE FROM akun WHERE id = ? AND organization_id = ?')
      .bind(akunId, orgId)
      .run();
    return json({ message: 'Akun berhasil dihapus' });
  }

  return errorResponse('Endpoint akun tidak ditemukan', 404);
}

// Hitung saldo akun = saldo_awal + semua transaksi yang menyentuh akun ini
async function hitungSaldoAkun(db, akun) {
  let saldo = akun.saldo_awal;

  const masuk = await db
    .prepare(
      `SELECT COALESCE(SUM(jumlah),0) as total FROM transaksi
       WHERE akun_id = ? AND tipe IN ('masuk','penyesuaian') AND jumlah > 0`
    )
    .bind(akun.id)
    .first();
  saldo += masuk.total || 0;

  const keluar = await db
    .prepare(
      `SELECT COALESCE(SUM(jumlah),0) as total FROM transaksi
       WHERE akun_id = ? AND tipe = 'keluar'`
    )
    .bind(akun.id)
    .first();
  saldo -= keluar.total || 0;

  const penyesuaianNegatif = await db
    .prepare(
      `SELECT COALESCE(SUM(jumlah),0) as total FROM transaksi
       WHERE akun_id = ? AND tipe = 'penyesuaian' AND jumlah < 0`
    )
    .bind(akun.id)
    .first();
  // sudah tercakup di 'masuk' filter jumlah>0 di atas jadi penyesuaian negatif perlu dikurangi terpisah
  saldo += penyesuaianNegatif.total || 0; // total ini negatif, jadi menambah = mengurangi

  const transferKeluar = await db
    .prepare(
      `SELECT COALESCE(SUM(jumlah),0) as total FROM transaksi
       WHERE akun_asal_id = ? AND tipe = 'transfer'`
    )
    .bind(akun.id)
    .first();
  saldo -= transferKeluar.total || 0;

  const transferMasuk = await db
    .prepare(
      `SELECT COALESCE(SUM(jumlah),0) as total FROM transaksi
       WHERE akun_tujuan_id = ? AND tipe = 'transfer'`
    )
    .bind(akun.id)
    .first();
  saldo += transferMasuk.total || 0;

  return saldo;
}

// ============================================================
// KATEGORI ROUTES
// ============================================================
async function handleKategori(request, env, path, orgId) {
  if (path === '/api/kategori' && request.method === 'GET') {
    const url = new URL(request.url);
    const tipe = url.searchParams.get('tipe');
    let query = 'SELECT * FROM kategori WHERE organization_id = ?';
    const params = [orgId];
    if (tipe) {
      query += ' AND tipe = ?';
      params.push(tipe);
    }
    query += ' ORDER BY nama';
    const rows = await env.DB.prepare(query).bind(...params).all();
    return json({ kategori: rows.results || [] });
  }

  if (path === '/api/kategori' && request.method === 'POST') {
    const body = await request.json().catch(() => ({}));
    const { nama, tipe } = body;
    if (!nama || !nama.trim()) return errorResponse('Nama kategori wajib diisi');
    if (!['pemasukan', 'pengeluaran'].includes(tipe)) return errorResponse('Tipe kategori tidak valid');

    const existing = await env.DB.prepare(
      'SELECT id FROM kategori WHERE organization_id = ? AND nama = ? AND tipe = ?'
    )
      .bind(orgId, nama.trim(), tipe)
      .first();
    if (existing) return errorResponse('Kategori ini sudah ada', 409);

    const id = newId();
    await env.DB.prepare(
      'INSERT INTO kategori (id, organization_id, nama, tipe) VALUES (?, ?, ?, ?)'
    )
      .bind(id, orgId, nama.trim(), tipe)
      .run();
    return json({ id, nama: nama.trim(), tipe }, 201);
  }

  const delMatch = path.match(/^\/api\/kategori\/([^/]+)$/);
  if (delMatch && request.method === 'DELETE') {
    const kategoriId = delMatch[1];
    const dipakai = await env.DB.prepare(
      'SELECT id FROM transaksi WHERE organization_id = ? AND kategori_id = ? LIMIT 1'
    )
      .bind(orgId, kategoriId)
      .first();
    if (dipakai) return errorResponse('Kategori ini sudah dipakai transaksi, tidak bisa dihapus', 409);
    await env.DB.prepare('DELETE FROM kategori WHERE id = ? AND organization_id = ?')
      .bind(kategoriId, orgId)
      .run();
    return json({ message: 'Kategori berhasil dihapus' });
  }

  return errorResponse('Endpoint kategori tidak ditemukan', 404);
}

// ============================================================
// ANGGOTA ROUTES
// ============================================================
async function handleAnggota(request, env, path, orgId, user) {
  // GET /api/anggota  -> list + search + pagination + statistik header
  if (path === '/api/anggota' && request.method === 'GET') {
    const url = new URL(request.url);
    const search = url.searchParams.get('search') || '';
    const page = parseInt(url.searchParams.get('page') || '1', 10);
    const perPage = parseInt(url.searchParams.get('per_page') || '6', 10);

    let query = `SELECT * FROM anggota WHERE organization_id = ? AND dikeluarkan_at IS NULL`;
    const params = [orgId];
    if (search) {
      query += ` AND (nama LIKE ? OR no_hp LIKE ?)`;
      params.push(`%${search}%`, `%${search}%`);
    }

    const countRow = await env.DB.prepare(
      query.replace('SELECT *', 'SELECT COUNT(*) as total')
    )
      .bind(...params)
      .first();
    const total = countRow.total || 0;

    query += ' ORDER BY nama LIMIT ? OFFSET ?';
    params.push(perPage, (page - 1) * perPage);
    const rows = await env.DB.prepare(query).bind(...params).all();

    const settings = await env.DB.prepare(
      'SELECT * FROM iuran_settings WHERE organization_id = ?'
    )
      .bind(orgId)
      .first();

    const anggotaDenganStatus = [];
    for (const a of rows.results || []) {
      const status = await hitungStatusIuran(env.DB, a, settings, orgId);
      anggotaDenganStatus.push({ ...a, status_iuran: status });
    }

    // Statistik ringkas header
    const semuaAnggota = await env.DB.prepare(
      'SELECT * FROM anggota WHERE organization_id = ? AND dikeluarkan_at IS NULL'
    )
      .bind(orgId)
      .all();
    let sudahBayar = 0;
    let menunggak = 0;
    let totalIuranBulanIni = 0;
    const bulanIni = new Date().toISOString().slice(0, 7);
    for (const a of semuaAnggota.results || []) {
      const status = await hitungStatusIuran(env.DB, a, settings, orgId);
      if (status.status_bulan_ini === 'lunas') sudahBayar++;
      else menunggak++;
    }
    const totalIuranRow = await env.DB.prepare(
      `SELECT COALESCE(SUM(jumlah_dialokasikan),0) as total FROM iuran_alokasi
       WHERE anggota_id IN (SELECT id FROM anggota WHERE organization_id = ?) AND periode = ?`
    )
      .bind(orgId, bulanIni)
      .first();
    totalIuranBulanIni = totalIuranRow.total || 0;

    return json({
      anggota: anggotaDenganStatus,
      pagination: { page, per_page: perPage, total },
      statistik: {
        total_anggota: semuaAnggota.results.length,
        sudah_bayar: sudahBayar,
        menunggak,
        total_iuran_bulan_ini: totalIuranBulanIni,
      },
    });
  }

  // POST /api/anggota
  if (path === '/api/anggota' && request.method === 'POST') {
    const body = await request.json().catch(() => ({}));
    const { nama, no_hp, tanggal_bergabung } = body;
    if (!nama || !nama.trim()) return errorResponse('Nama anggota wajib diisi');
    if (!tanggal_bergabung) return errorResponse('Tanggal bergabung wajib diisi');

    const id = newId();
    await env.DB.prepare(
      `INSERT INTO anggota (id, organization_id, nama, no_hp, tanggal_bergabung)
       VALUES (?, ?, ?, ?, ?)`
    )
      .bind(id, orgId, nama.trim(), no_hp || null, tanggal_bergabung)
      .run();
    return json({ id }, 201);
  }

  // PUT /api/anggota/:id
  const putMatch = path.match(/^\/api\/anggota\/([^/]+)$/);
  if (putMatch && request.method === 'PUT') {
    const anggotaId = putMatch[1];
    const anggota = await env.DB.prepare(
      'SELECT * FROM anggota WHERE id = ? AND organization_id = ?'
    )
      .bind(anggotaId, orgId)
      .first();
    if (!anggota) return errorResponse('Anggota tidak ditemukan', 404);

    const body = await request.json().catch(() => ({}));
    const fields = [];
    const values = [];
    if (typeof body.nama === 'string' && body.nama.trim()) {
      fields.push('nama = ?');
      values.push(body.nama.trim());
    }
    if (typeof body.no_hp === 'string') {
      fields.push('no_hp = ?');
      values.push(body.no_hp);
    }
    if (typeof body.tanggal_bergabung === 'string' && body.tanggal_bergabung) {
      fields.push('tanggal_bergabung = ?');
      values.push(body.tanggal_bergabung);
    }
    if (fields.length === 0) return errorResponse('Tidak ada data untuk diubah');
    fields.push('updated_at = datetime("now")');
    values.push(anggotaId, orgId);
    await env.DB.prepare(
      `UPDATE anggota SET ${fields.join(', ')} WHERE id = ? AND organization_id = ?`
    ).bind(...values).run();
    return json({ message: 'Data anggota berhasil diperbarui' });
  }

  // DELETE /api/anggota/:id  -> soft delete (dikeluarkan_at), riwayat transaksi TETAP ada
  const delMatch = path.match(/^\/api\/anggota\/([^/]+)$/);
  if (delMatch && request.method === 'DELETE') {
    const anggotaId = delMatch[1];
    const anggota = await env.DB.prepare(
      'SELECT * FROM anggota WHERE id = ? AND organization_id = ?'
    )
      .bind(anggotaId, orgId)
      .first();
    if (!anggota) return errorResponse('Anggota tidak ditemukan', 404);
    await env.DB.prepare(
      'UPDATE anggota SET dikeluarkan_at = datetime("now") WHERE id = ? AND organization_id = ?'
    )
      .bind(anggotaId, orgId)
      .run();
    return json({ message: 'Anggota berhasil dikeluarkan. Riwayat transaksinya tetap tersimpan.' });
  }

  // GET /api/anggota/:id/riwayat  -> riwayat pembayaran iuran per anggota
  const riwayatMatch = path.match(/^\/api\/anggota\/([^/]+)\/riwayat$/);
  if (riwayatMatch && request.method === 'GET') {
    const anggotaId = riwayatMatch[1];
    const anggota = await env.DB.prepare(
      'SELECT * FROM anggota WHERE id = ? AND organization_id = ?'
    )
      .bind(anggotaId, orgId)
      .first();
    if (!anggota) return errorResponse('Anggota tidak ditemukan', 404);

    const pembayaran = await env.DB.prepare(
      `SELECT p.*, GROUP_CONCAT(a.periode || ':' || a.jumlah_dialokasikan || ':' || a.status) as alokasi
       FROM iuran_pembayaran p
       LEFT JOIN iuran_alokasi a ON a.pembayaran_id = p.id
       WHERE p.anggota_id = ? AND p.organization_id = ?
       GROUP BY p.id
       ORDER BY p.tanggal_pembayaran DESC`
    )
      .bind(anggotaId, orgId)
      .all();

    const settings = await env.DB.prepare(
      'SELECT * FROM iuran_settings WHERE organization_id = ?'
    )
      .bind(orgId)
      .first();
    const status = await hitungStatusIuran(env.DB, anggota, settings, orgId);

    return json({
      anggota,
      status_iuran: status,
      riwayat_pembayaran: (pembayaran.results || []).map((p) => ({
        ...p,
        alokasi: p.alokasi
          ? p.alokasi.split(',').map((s) => {
              const [periode, jumlah, st] = s.split(':');
              return { periode, jumlah_dialokasikan: parseInt(jumlah, 10), status: st };
            })
          : [],
      })),
    });
  }

  return errorResponse('Endpoint anggota tidak ditemukan', 404);
}

// Hitung status iuran anggota: tunggakan berapa bulan/rupiah, terakhir bayar, lunas sampai kapan
async function hitungStatusIuran(db, anggota, settings, orgId) {
  if (!settings || !settings.nominal_bulanan || settings.nominal_bulanan <= 0) {
    return {
      status_bulan_ini: 'tidak_dikenakan',
      lunas_sampai: null,
      tunggakan_bulan: 0,
      tunggakan_rupiah: 0,
      terakhir_bayar: null,
    };
  }

  // Periode wajib bayar: mulai dari MAX(tanggal_bergabung anggota, tanggal_mulai_organisasi)
  const mulaiAnggota = anggota.tanggal_bergabung > settings.tanggal_mulai_organisasi
    ? anggota.tanggal_bergabung
    : settings.tanggal_mulai_organisasi;

  const periodeList = generatePeriodeList(mulaiAnggota, new Date().toISOString().slice(0, 10));

  const alokasiRows = await db
    .prepare(
      `SELECT periode, SUM(jumlah_dialokasikan) as total FROM iuran_alokasi
       WHERE anggota_id = ? GROUP BY periode`
    )
    .bind(anggota.id)
    .all();
  const alokasiMap = {};
  for (const row of alokasiRows.results || []) alokasiMap[row.periode] = row.total;

  let tunggakanBulan = 0;
  let tunggakanRupiah = 0;
  let lunasSampai = null;
  let statusBulanIni = 'lunas';
  const bulanIni = new Date().toISOString().slice(0, 7);

  for (const periode of periodeList) {
    const dibayar = alokasiMap[periode] || 0;
    const kurang = settings.nominal_bulanan - dibayar;
    if (kurang > 0) {
      tunggakanBulan++;
      tunggakanRupiah += kurang;
      if (periode === bulanIni) statusBulanIni = dibayar > 0 ? 'sebagian' : 'belum_bayar';
      else statusBulanIni = 'belum_bayar'; // ada tunggakan periode lama -> anggap belum lunas
    } else {
      lunasSampai = periode;
    }
  }
  if (tunggakanBulan === 0) statusBulanIni = 'lunas';

  const terakhirBayarRow = await db
    .prepare(
      `SELECT tanggal_pembayaran FROM iuran_pembayaran WHERE anggota_id = ?
       ORDER BY tanggal_pembayaran DESC LIMIT 1`
    )
    .bind(anggota.id)
    .first();

  return {
    status_bulan_ini: statusBulanIni,
    lunas_sampai: lunasSampai,
    tunggakan_bulan: tunggakanBulan,
    tunggakan_rupiah: tunggakanRupiah,
    terakhir_bayar: terakhirBayarRow ? terakhirBayarRow.tanggal_pembayaran : null,
  };
}

// Generate list periode 'YYYY-MM' dari mulai s.d. sekarang (inklusif)
function generatePeriodeList(tanggalMulai, tanggalSekarang) {
  const hasil = [];
  const mulai = new Date(tanggalMulai + 'T00:00:00Z');
  const sekarang = new Date(tanggalSekarang + 'T00:00:00Z');
  let cursor = new Date(Date.UTC(mulai.getUTCFullYear(), mulai.getUTCMonth(), 1));
  const batas = new Date(Date.UTC(sekarang.getUTCFullYear(), sekarang.getUTCMonth(), 1));
  while (cursor <= batas) {
    const y = cursor.getUTCFullYear();
    const m = String(cursor.getUTCMonth() + 1).padStart(2, '0');
    hasil.push(`${y}-${m}`);
    cursor.setUTCMonth(cursor.getUTCMonth() + 1);
  }
  return hasil;
}

// ============================================================
// IURAN ROUTES (settings & pembayaran)
// ============================================================
async function handleIuran(request, env, path, orgId, user) {
  // GET /api/iuran/settings
  if (path === '/api/iuran/settings' && request.method === 'GET') {
    const settings = await env.DB.prepare(
      'SELECT * FROM iuran_settings WHERE organization_id = ?'
    )
      .bind(orgId)
      .first();
    return json({ settings });
  }

  // PUT /api/iuran/settings  (= form "Penyesuaian Iuran Pokok" di tab Penyesuaian)
  if (path === '/api/iuran/settings' && request.method === 'PUT') {
    const body = await request.json().catch(() => ({}));
    const { nominal_bulanan, tanggal_mulai_organisasi } = body;
    if (nominal_bulanan === undefined || nominal_bulanan < 0) {
      return errorResponse('Nominal iuran wajib diisi dan tidak boleh negatif');
    }
    if (!tanggal_mulai_organisasi) return errorResponse('Tanggal mulai organisasi wajib diisi');

    await env.DB.prepare(
      `UPDATE iuran_settings SET nominal_bulanan = ?, tanggal_mulai_organisasi = ?,
       updated_at = datetime("now"), updated_by = ? WHERE organization_id = ?`
    )
      .bind(nominal_bulanan, tanggal_mulai_organisasi, user.id, orgId)
      .run();
    return json({ message: 'Pengaturan iuran berhasil diperbarui' });
  }

  // POST /api/iuran/bayar  (= form "Iuran Anggota" di tab Catat)
  if (path === '/api/iuran/bayar' && request.method === 'POST') {
    const body = await request.json().catch(() => ({}));
    const { anggota_id, jumlah, akun_id, tanggal_pembayaran, metode_pembayaran, catatan } = body;
    if (!anggota_id || !jumlah || jumlah <= 0 || !akun_id || !tanggal_pembayaran) {
      return errorResponse('Data pembayaran iuran tidak lengkap');
    }

    const anggota = await env.DB.prepare(
      'SELECT * FROM anggota WHERE id = ? AND organization_id = ? AND dikeluarkan_at IS NULL'
    )
      .bind(anggota_id, orgId)
      .first();
    if (!anggota) return errorResponse('Anggota tidak ditemukan', 404);

    const akun = await env.DB.prepare('SELECT * FROM akun WHERE id = ? AND organization_id = ?')
      .bind(akun_id, orgId)
      .first();
    if (!akun) return errorResponse('Akun tidak ditemukan', 404);

    const settings = await env.DB.prepare(
      'SELECT * FROM iuran_settings WHERE organization_id = ?'
    )
      .bind(orgId)
      .first();
    if (!settings || !settings.nominal_bulanan || settings.nominal_bulanan <= 0) {
      return errorResponse('Nominal iuran wajib belum diatur. Atur dulu di menu Penyesuaian.', 400);
    }

    // --- Alokasi FIFO ke periode tertua yang belum lunas ---
    const mulaiAnggota = anggota.tanggal_bergabung > settings.tanggal_mulai_organisasi
      ? anggota.tanggal_bergabung
      : settings.tanggal_mulai_organisasi;
    const periodeList = generatePeriodeList(mulaiAnggota, tanggal_pembayaran.slice(0, 10));

    const alokasiRows = await env.DB.prepare(
      `SELECT periode, SUM(jumlah_dialokasikan) as total FROM iuran_alokasi
       WHERE anggota_id = ? GROUP BY periode`
    )
      .bind(anggota_id)
      .all();
    const alokasiMap = {};
    for (const row of alokasiRows.results || []) alokasiMap[row.periode] = row.total;

    let sisaDana = jumlah;
    const alokasiBaru = [];
    for (const periode of periodeList) {
      if (sisaDana <= 0) break;
      const sudahDibayar = alokasiMap[periode] || 0;
      const kekurangan = settings.nominal_bulanan - sudahDibayar;
      if (kekurangan <= 0) continue;
      const dialokasikan = Math.min(sisaDana, kekurangan);
      alokasiBaru.push({
        periode,
        jumlah: dialokasikan,
        status: dialokasikan >= kekurangan ? 'lunas' : 'sebagian',
      });
      sisaDana -= dialokasikan;
    }
    // Kalau masih ada sisa dana setelah semua periode wajib lunas -> alokasikan ke periode berikutnya (bayar di muka)
    let periodeLanjut = periodeList.length > 0 ? periodeList[periodeList.length - 1] : mulaiAnggota.slice(0, 7);
    while (sisaDana > 0) {
      periodeLanjut = tambahSatuBulan(periodeLanjut);
      const dialokasikan = Math.min(sisaDana, settings.nominal_bulanan);
      alokasiBaru.push({
        periode: periodeLanjut,
        jumlah: dialokasikan,
        status: dialokasikan >= settings.nominal_bulanan ? 'lunas' : 'sebagian',
      });
      sisaDana -= dialokasikan;
    }

    // --- Simpan: transaksi kas + iuran_pembayaran + iuran_alokasi (1 pembayaran = 1 pencatatan kas) ---
    const transaksiId = newId();
    const noReferensi = await generateNoReferensi(env.DB, tanggal_pembayaran.slice(0, 10));
    const pembayaranId = newId();

    const periodeTerbayarStr = alokasiBaru.map((a) => a.periode).join(', ');

    const statements = [
      env.DB.prepare(
        `INSERT INTO transaksi (id, organization_id, tipe, sumber, jumlah, akun_id,
         metode_pembayaran, keterangan, catatan, no_referensi, tanggal, dicatat_oleh)
         VALUES (?, ?, 'masuk', 'iuran', ?, ?, ?, ?, ?, ?, ?, ?)`
      ).bind(
        transaksiId,
        orgId,
        jumlah,
        akun_id,
        metode_pembayaran || 'Tunai',
        `Iuran ${anggota.nama} (${periodeTerbayarStr})`,
        catatan || null,
        noReferensi,
        tanggal_pembayaran,
        user.id
      ),
      env.DB.prepare(
        `INSERT INTO iuran_pembayaran (id, organization_id, anggota_id, akun_id, jumlah,
         metode_pembayaran, tanggal_pembayaran, catatan, dicatat_oleh, transaksi_id)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      ).bind(
        pembayaranId,
        orgId,
        anggota_id,
        akun_id,
        jumlah,
        metode_pembayaran || 'Tunai',
        tanggal_pembayaran,
        catatan || null,
        user.id,
        transaksiId
      ),
    ];
    for (const a of alokasiBaru) {
      statements.push(
        env.DB.prepare(
          `INSERT INTO iuran_alokasi (id, pembayaran_id, anggota_id, periode, jumlah_dialokasikan, status)
           VALUES (?, ?, ?, ?, ?, ?)`
        ).bind(newId(), pembayaranId, anggota_id, a.periode, a.jumlah, a.status)
      );
    }
    await env.DB.batch(statements);

    return json(
      {
        message: 'Pembayaran iuran berhasil dicatat',
        transaksi_id: transaksiId,
        no_referensi: noReferensi,
        alokasi: alokasiBaru,
      },
      201
    );
  }

  return errorResponse('Endpoint iuran tidak ditemukan', 404);
}

function tambahSatuBulan(periodeYYYYMM) {
  const [y, m] = periodeYYYYMM.split('-').map(Number);
  const d = new Date(Date.UTC(y, m - 1 + 1, 1));
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
}

// ============================================================
// TRANSAKSI ROUTES
// ============================================================
async function handleTransaksi(request, env, path, orgId, user) {
  // GET /api/transaksi  -> riwayat + filter tipe + pagination
  if (path === '/api/transaksi' && request.method === 'GET') {
    const url = new URL(request.url);
    const tipe = url.searchParams.get('tipe'); // masuk|keluar|transfer|penyesuaian
    const page = parseInt(url.searchParams.get('page') || '1', 10);
    const perPage = parseInt(url.searchParams.get('per_page') || '20', 10);

    let query = `SELECT t.*, k.nama as kategori_nama, a.nama as akun_nama,
      aa.nama as akun_asal_nama, at.nama as akun_tujuan_nama, u.nama as dicatat_oleh_nama
      FROM transaksi t
      LEFT JOIN kategori k ON k.id = t.kategori_id
      LEFT JOIN akun a ON a.id = t.akun_id
      LEFT JOIN akun aa ON aa.id = t.akun_asal_id
      LEFT JOIN akun at ON at.id = t.akun_tujuan_id
      LEFT JOIN users u ON u.id = t.dicatat_oleh
      WHERE t.organization_id = ?`;
    const params = [orgId];
    if (tipe && tipe !== 'semua') {
      query += ' AND t.tipe = ?';
      params.push(tipe);
    }
    query += ' ORDER BY t.tanggal DESC, t.created_at DESC LIMIT ? OFFSET ?';
    params.push(perPage, (page - 1) * perPage);

    const rows = await env.DB.prepare(query).bind(...params).all();
    return json({ transaksi: rows.results || [] });
  }

  // GET /api/transaksi/:id  -> detail 1 transaksi (buat struk)
  const detailMatch = path.match(/^\/api\/transaksi\/([^/]+)$/);
  if (detailMatch && request.method === 'GET') {
    const id = detailMatch[1];
    const row = await env.DB.prepare(
      `SELECT t.*, k.nama as kategori_nama, a.nama as akun_nama,
        aa.nama as akun_asal_nama, at.nama as akun_tujuan_nama, u.nama as dicatat_oleh_nama
       FROM transaksi t
       LEFT JOIN kategori k ON k.id = t.kategori_id
       LEFT JOIN akun a ON a.id = t.akun_id
       LEFT JOIN akun aa ON aa.id = t.akun_asal_id
       LEFT JOIN akun at ON at.id = t.akun_tujuan_id
       LEFT JOIN users u ON u.id = t.dicatat_oleh
       WHERE t.id = ? AND t.organization_id = ?`
    )
      .bind(id, orgId)
      .first();
    if (!row) return errorResponse('Transaksi tidak ditemukan', 404);

    // Kalau transaksi ini asalnya dari pembayaran iuran, sertakan info anggota
    let iuranInfo = null;
    if (row.sumber === 'iuran') {
      iuranInfo = await env.DB.prepare(
        `SELECT p.*, an.nama as anggota_nama, an.no_hp as anggota_no_hp
         FROM iuran_pembayaran p JOIN anggota an ON an.id = p.anggota_id
         WHERE p.transaksi_id = ?`
      )
        .bind(id)
        .first();
    }

    return json({ transaksi: row, iuran_info: iuranInfo });
  }

  // POST /api/transaksi/pemasukan-lain
  if (path === '/api/transaksi/pemasukan-lain' && request.method === 'POST') {
    return simpanTransaksiSederhana(env, orgId, user, 'masuk', await request.json().catch(() => ({})));
  }

  // POST /api/transaksi/pengeluaran
  if (path === '/api/transaksi/pengeluaran' && request.method === 'POST') {
    return simpanTransaksiSederhana(env, orgId, user, 'keluar', await request.json().catch(() => ({})));
  }

  // POST /api/transaksi/transfer
  if (path === '/api/transaksi/transfer' && request.method === 'POST') {
    const body = await request.json().catch(() => ({}));
    const { akun_asal_id, akun_tujuan_id, jumlah, tanggal, catatan } = body;
    if (!akun_asal_id || !akun_tujuan_id || !jumlah || jumlah <= 0 || !tanggal) {
      return errorResponse('Data transfer tidak lengkap');
    }
    if (akun_asal_id === akun_tujuan_id) return errorResponse('Akun asal dan tujuan tidak boleh sama');

    const [asal, tujuan] = await Promise.all([
      env.DB.prepare('SELECT * FROM akun WHERE id = ? AND organization_id = ?').bind(akun_asal_id, orgId).first(),
      env.DB.prepare('SELECT * FROM akun WHERE id = ? AND organization_id = ?').bind(akun_tujuan_id, orgId).first(),
    ]);
    if (!asal || !tujuan) return errorResponse('Akun tidak ditemukan', 404);

    const id = newId();
    const noReferensi = await generateNoReferensi(env.DB, tanggal.slice(0, 10));
    await env.DB.prepare(
      `INSERT INTO transaksi (id, organization_id, tipe, jumlah, akun_asal_id, akun_tujuan_id,
       keterangan, catatan, no_referensi, tanggal, dicatat_oleh)
       VALUES (?, ?, 'transfer', ?, ?, ?, ?, ?, ?, ?, ?)`
    )
      .bind(
        id,
        orgId,
        jumlah,
        akun_asal_id,
        akun_tujuan_id,
        `Transfer ${asal.nama} -> ${tujuan.nama}`,
        catatan || null,
        noReferensi,
        tanggal,
        user.id
      )
      .run();
    return json({ id, no_referensi: noReferensi }, 201);
  }

  // POST /api/transaksi/penyesuaian  (penyesuaian saldo akun, BUKAN pengaturan iuran pokok)
  if (path === '/api/transaksi/penyesuaian' && request.method === 'POST') {
    const body = await request.json().catch(() => ({}));
    const { akun_id, jumlah, tanggal, catatan } = body;
    if (!akun_id || jumlah === undefined || jumlah === 0 || !tanggal) {
      return errorResponse('Data penyesuaian tidak lengkap (jumlah tidak boleh 0)');
    }
    const akun = await env.DB.prepare('SELECT * FROM akun WHERE id = ? AND organization_id = ?')
      .bind(akun_id, orgId)
      .first();
    if (!akun) return errorResponse('Akun tidak ditemukan', 404);

    const id = newId();
    const noReferensi = await generateNoReferensi(env.DB, tanggal.slice(0, 10));
    await env.DB.prepare(
      `INSERT INTO transaksi (id, organization_id, tipe, jumlah, akun_id,
       keterangan, catatan, no_referensi, tanggal, dicatat_oleh)
       VALUES (?, ?, 'penyesuaian', ?, ?, ?, ?, ?, ?, ?)`
    )
      .bind(
        id,
        orgId,
        jumlah, // boleh negatif = koreksi mengurangi saldo
        akun_id,
        jumlah > 0 ? `Penyesuaian tambah saldo ${akun.nama}` : `Penyesuaian kurangi saldo ${akun.nama}`,
        catatan || null,
        noReferensi,
        tanggal,
        user.id
      )
      .run();
    return json({ id, no_referensi: noReferensi }, 201);
  }

  return errorResponse('Endpoint transaksi tidak ditemukan', 404);
}

async function simpanTransaksiSederhana(env, orgId, user, tipe, body) {
  const { jumlah, akun_id, kategori_id, keterangan, metode_pembayaran, tanggal, catatan } = body;
  if (!jumlah || jumlah <= 0 || !akun_id || !tanggal || !keterangan) {
    return errorResponse('Data transaksi tidak lengkap');
  }
  const akun = await env.DB.prepare('SELECT * FROM akun WHERE id = ? AND organization_id = ?')
    .bind(akun_id, orgId)
    .first();
  if (!akun) return errorResponse('Akun tidak ditemukan', 404);

  if (kategori_id) {
    const kategori = await env.DB.prepare('SELECT * FROM kategori WHERE id = ? AND organization_id = ?')
      .bind(kategori_id, orgId)
      .first();
    if (!kategori) return errorResponse('Kategori tidak ditemukan', 404);
  }

  const id = newId();
  const noReferensi = await generateNoReferensi(env.DB, tanggal.slice(0, 10));
  await env.DB.prepare(
    `INSERT INTO transaksi (id, organization_id, tipe, jumlah, akun_id, kategori_id,
     metode_pembayaran, keterangan, catatan, no_referensi, tanggal, dicatat_oleh)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  )
    .bind(
      id,
      orgId,
      tipe,
      jumlah,
      akun_id,
      kategori_id || null,
      metode_pembayaran || 'Tunai',
      keterangan.trim(),
      catatan || null,
      noReferensi,
      tanggal,
      user.id
    )
    .run();
  return json({ id, no_referensi: noReferensi }, 201);
}

// ============================================================
// DASHBOARD (BERANDA) ROUTES
// ============================================================
async function handleDashboard(request, env, path, orgId, user) {
  if (path === '/api/dashboard/summary' && request.method === 'GET') {
    const akunList = await env.DB.prepare('SELECT * FROM akun WHERE organization_id = ?')
      .bind(orgId)
      .all();
    let totalSaldo = 0;
    for (const akun of akunList.results || []) {
      totalSaldo += await hitungSaldoAkun(env.DB, akun);
    }

    const bulanIni = new Date().toISOString().slice(0, 7);
    const awalBulan = `${bulanIni}-01`;
    const hariIni = new Date().toISOString().slice(0, 10);

    const pemasukanBulanIni = await env.DB.prepare(
      `SELECT COALESCE(SUM(jumlah),0) as total FROM transaksi
       WHERE organization_id = ? AND tipe = 'masuk' AND tanggal >= ?`
    )
      .bind(orgId, awalBulan)
      .first();
    const pengeluaranBulanIni = await env.DB.prepare(
      `SELECT COALESCE(SUM(jumlah),0) as total FROM transaksi
       WHERE organization_id = ? AND tipe = 'keluar' AND tanggal >= ?`
    )
      .bind(orgId, awalBulan)
      .first();

    // Saldo awal bulan = total saldo saat ini dikurangi pergerakan bersih bulan ini
    const saldoAwalBulan = totalSaldo - (pemasukanBulanIni.total || 0) + (pengeluaranBulanIni.total || 0);

    const transaksiTerbaru = await env.DB.prepare(
      `SELECT t.*, k.nama as kategori_nama FROM transaksi t
       LEFT JOIN kategori k ON k.id = t.kategori_id
       WHERE t.organization_id = ? ORDER BY t.tanggal DESC, t.created_at DESC LIMIT 5`
    )
      .bind(orgId)
      .all();

    const adaTransaksiHariIni = await env.DB.prepare(
      `SELECT id FROM transaksi WHERE organization_id = ? AND tanggal = ? LIMIT 1`
    )
      .bind(orgId, hariIni)
      .first();

    return json({
      total_saldo: totalSaldo,
      total_pemasukan: pemasukanBulanIni.total || 0,
      total_pengeluaran: pengeluaranBulanIni.total || 0,
      ringkasan_bulan_ini: {
        bulan: bulanIni,
        pemasukan: pemasukanBulanIni.total || 0,
        pengeluaran: pengeluaranBulanIni.total || 0,
        saldo_awal: saldoAwalBulan,
        saldo_akhir: totalSaldo,
      },
      transaksi_terbaru: transaksiTerbaru.results || [],
      ada_catatan_hari_ini: !!adaTransaksiHariIni,
    });
  }

  return errorResponse('Endpoint dashboard tidak ditemukan', 404);
}

// ============================================================
// LAPORAN ROUTES
// ============================================================
async function handleLaporan(request, env, path, orgId, user) {
  const url = new URL(request.url);

  // GET /api/laporan/ringkasan?dari=YYYY-MM-DD&sampai=YYYY-MM-DD&granularitas=mingguan|bulanan|tahunan
  if (path === '/api/laporan/ringkasan' && request.method === 'GET') {
    const dari = url.searchParams.get('dari');
    const sampai = url.searchParams.get('sampai');
    const granularitas = url.searchParams.get('granularitas') || 'mingguan';
    if (!dari || !sampai) return errorResponse('Parameter dari dan sampai wajib diisi');

    const totalMasuk = await env.DB.prepare(
      `SELECT COALESCE(SUM(jumlah),0) as total, COUNT(*) as jumlah_transaksi FROM transaksi
       WHERE organization_id = ? AND tipe = 'masuk' AND tanggal BETWEEN ? AND ?`
    )
      .bind(orgId, dari, sampai)
      .first();
    const totalKeluar = await env.DB.prepare(
      `SELECT COALESCE(SUM(jumlah),0) as total, COUNT(*) as jumlah_transaksi FROM transaksi
       WHERE organization_id = ? AND tipe = 'keluar' AND tanggal BETWEEN ? AND ?`
    )
      .bind(orgId, dari, sampai)
      .first();

    // Periode pembanding: rentang waktu yang sama persis, mundur ke belakang
    const rentangHari = Math.round((new Date(sampai) - new Date(dari)) / 86400000) + 1;
    const dariPembanding = new Date(new Date(dari).getTime() - rentangHari * 86400000)
      .toISOString()
      .slice(0, 10);
    const sampaiPembanding = new Date(new Date(dari).getTime() - 86400000).toISOString().slice(0, 10);

    const masukPembanding = await env.DB.prepare(
      `SELECT COALESCE(SUM(jumlah),0) as total FROM transaksi
       WHERE organization_id = ? AND tipe = 'masuk' AND tanggal BETWEEN ? AND ?`
    )
      .bind(orgId, dariPembanding, sampaiPembanding)
      .first();
    const keluarPembanding = await env.DB.prepare(
      `SELECT COALESCE(SUM(jumlah),0) as total FROM transaksi
       WHERE organization_id = ? AND tipe = 'keluar' AND tanggal BETWEEN ? AND ?`
    )
      .bind(orgId, dariPembanding, sampaiPembanding)
      .first();

    const akunList = await env.DB.prepare('SELECT * FROM akun WHERE organization_id = ?').bind(orgId).all();
    let saldoAkhir = 0;
    for (const akun of akunList.results || []) saldoAkhir += await hitungSaldoAkun(env.DB, akun);
    const saldoPembanding = saldoAkhir - (totalMasuk.total || 0) + (totalKeluar.total || 0);

    function hitungPersenGrowth(sekarang, dulu) {
      if (!dulu || dulu === 0) return sekarang > 0 ? 100 : 0;
      return Math.round(((sekarang - dulu) / dulu) * 100);
    }

    // Grafik arus kas per bucket waktu (sederhana: kelompokkan per tanggal, agregasi kasar di frontend)
    const grafikRows = await env.DB.prepare(
      `SELECT tanggal, tipe, SUM(jumlah) as total FROM transaksi
       WHERE organization_id = ? AND tipe IN ('masuk','keluar') AND tanggal BETWEEN ? AND ?
       GROUP BY tanggal, tipe ORDER BY tanggal`
    )
      .bind(orgId, dari, sampai)
      .all();

    // Rincian per kategori pengeluaran
    const kategoriRows = await env.DB.prepare(
      `SELECT k.nama, SUM(t.jumlah) as total FROM transaksi t
       JOIN kategori k ON k.id = t.kategori_id
       WHERE t.organization_id = ? AND t.tipe = 'keluar' AND t.tanggal BETWEEN ? AND ?
       GROUP BY k.nama ORDER BY total DESC`
    )
      .bind(orgId, dari, sampai)
      .all();

    return json({
      periode: { dari, sampai, granularitas },
      total_pemasukan: totalMasuk.total || 0,
      total_pengeluaran: totalKeluar.total || 0,
      saldo_akhir: saldoAkhir,
      growth: {
        pemasukan: hitungPersenGrowth(totalMasuk.total || 0, masukPembanding.total || 0),
        pengeluaran: hitungPersenGrowth(totalKeluar.total || 0, keluarPembanding.total || 0),
        saldo: hitungPersenGrowth(saldoAkhir, saldoPembanding),
      },
      grafik_arus_kas: grafikRows.results || [],
      rincian_kategori_pengeluaran: kategoriRows.results || [],
    });
  }

  // GET /api/laporan/anggota  -> ringkasan status iuran semua anggota untuk laporan
  if (path === '/api/laporan/anggota' && request.method === 'GET') {
    const settings = await env.DB.prepare('SELECT * FROM iuran_settings WHERE organization_id = ?')
      .bind(orgId)
      .first();
    const anggotaList = await env.DB.prepare(
      'SELECT * FROM anggota WHERE organization_id = ? AND dikeluarkan_at IS NULL ORDER BY nama'
    )
      .bind(orgId)
      .all();

    let sudahBayar = 0;
    let belumBayar = 0;
    let totalSudahBayar = 0;
    let totalBelumBayar = 0;
    const detail = [];
    for (const a of anggotaList.results || []) {
      const status = await hitungStatusIuran(env.DB, a, settings, orgId);
      if (status.status_bulan_ini === 'lunas') {
        sudahBayar++;
        totalSudahBayar += settings ? settings.nominal_bulanan : 0;
      } else {
        belumBayar++;
        totalBelumBayar += status.tunggakan_rupiah;
      }
      detail.push({ ...a, status_iuran: status });
    }

    return json({
      ringkasan: { sudah_bayar: sudahBayar, belum_bayar: belumBayar, totalSudahBayar, totalBelumBayar },
      detail,
    });
  }

  // GET /api/laporan/akun -> saldo per akun untuk laporan
  if (path === '/api/laporan/akun' && request.method === 'GET') {
    const akunList = await env.DB.prepare('SELECT * FROM akun WHERE organization_id = ?').bind(orgId).all();
    const hasil = [];
    for (const akun of akunList.results || []) {
      hasil.push({ ...akun, saldo: await hitungSaldoAkun(env.DB, akun) });
    }
    return json({ akun: hasil });
  }

  // GET /api/laporan/transaksi -> semua transaksi dalam rentang, untuk isi laporan lengkap
  if (path === '/api/laporan/transaksi' && request.method === 'GET') {
    const dari = url.searchParams.get('dari');
    const sampai = url.searchParams.get('sampai');
    if (!dari || !sampai) return errorResponse('Parameter dari dan sampai wajib diisi');
    const rows = await env.DB.prepare(
      `SELECT t.*, k.nama as kategori_nama, a.nama as akun_nama
       FROM transaksi t
       LEFT JOIN kategori k ON k.id = t.kategori_id
       LEFT JOIN akun a ON a.id = t.akun_id
       WHERE t.organization_id = ? AND t.tanggal BETWEEN ? AND ?
       ORDER BY t.tanggal, t.created_at`
    )
      .bind(orgId, dari, sampai)
      .all();
    return json({ transaksi: rows.results || [] });
  }

  return errorResponse('Endpoint laporan tidak ditemukan', 404);
}

// ============================================================
// NOTIFIKASI ROUTES (in-app)
// ============================================================
async function handleNotifikasi(request, env, path, orgId, user) {
  if (path === '/api/notifikasi' && request.method === 'GET') {
    const rows = await env.DB.prepare(
      'SELECT * FROM notifikasi WHERE organization_id = ? ORDER BY created_at DESC LIMIT 30'
    )
      .bind(orgId)
      .all();
    const belumDibaca = await env.DB.prepare(
      'SELECT COUNT(*) as total FROM notifikasi WHERE organization_id = ? AND dibaca = 0'
    )
      .bind(orgId)
      .first();
    return json({ notifikasi: rows.results || [], belum_dibaca: belumDibaca.total || 0 });
  }

  if (path === '/api/notifikasi/baca-semua' && request.method === 'POST') {
    await env.DB.prepare('UPDATE notifikasi SET dibaca = 1 WHERE organization_id = ?').bind(orgId).run();
    return json({ message: 'Semua notifikasi ditandai sudah dibaca' });
  }

  return errorResponse('Endpoint notifikasi tidak ditemukan', 404);
}

// Dipanggil terjadwal (cron trigger) atau setelah transaksi besar untuk generate notifikasi
async function generateNotifikasiOtomatis(env, orgId) {
  const settings = await env.DB.prepare('SELECT * FROM iuran_settings WHERE organization_id = ?')
    .bind(orgId)
    .first();
  if (!settings || !settings.nominal_bulanan) return;

  const anggotaList = await env.DB.prepare(
    'SELECT * FROM anggota WHERE organization_id = ? AND dikeluarkan_at IS NULL'
  )
    .bind(orgId)
    .all();

  for (const a of anggotaList.results || []) {
    const status = await hitungStatusIuran(env.DB, a, settings, orgId);
    if (status.status_bulan_ini !== 'lunas' && status.tunggakan_bulan >= 2) {
      const existing = await env.DB.prepare(
        `SELECT id FROM notifikasi WHERE organization_id = ? AND tipe = 'menunggak'
         AND pesan LIKE ? AND created_at > datetime('now', '-7 days')`
      )
        .bind(orgId, `%${a.nama}%`)
        .first();
      if (!existing) {
        await env.DB.prepare(
          `INSERT INTO notifikasi (id, organization_id, tipe, judul, pesan)
           VALUES (?, ?, 'menunggak', 'Anggota menunggak', ?)`
        )
          .bind(newId(), orgId, `${a.nama} menunggak iuran ${status.tunggakan_bulan} bulan`)
          .run();
      }
    }
  }
}

// ============================================================
// ROUTER UTAMA
// ============================================================
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === 'OPTIONS') {
      return json({});
    }

    try {
      // --- Serve foto profil dari R2 (publik, tanpa auth) ---
      if (path.startsWith('/r2/')) {
        const key = path.replace('/r2/', '');
        const object = await env.R2_FOTO.get(key);
        if (!object) return errorResponse('File tidak ditemukan', 404);
        return new Response(object.body, {
          headers: {
            'Content-Type': object.httpMetadata?.contentType || 'application/octet-stream',
            'Cache-Control': 'public, max-age=31536000',
          },
        });
      }

      // --- Rute publik (tanpa auth) ---
      if (path.startsWith('/api/auth/')) {
        return await handleAuth(request, env, path);
      }

      // --- Semua rute di bawah ini butuh auth ---
      const user = await getAuthUser(request, env);
      if (!user) return errorResponse('Unauthorized. Silakan login kembali.', 401);

      // --- Rute yang tidak butuh org aktif ---
      if (path === '/api/organizations' || path === '/api/organizations/gabung') {
        return await handleOrganizations(request, env, path, user);
      }
      if (path.startsWith('/api/profil')) {
        // org aktif opsional untuk profil (dipakai kalau ada, misal untuk tampilkan jabatan)
        const orgIdHeader = request.headers.get('X-Org-Id');
        let orgCtx = null;
        if (orgIdHeader) {
          orgCtx = await getActiveOrgId(request, env, user.id);
          if (orgCtx.error) orgCtx = null; // abaikan kalau org tidak valid, profil tetap bisa diakses
        }
        return await handleProfil(request, env, path, user, orgCtx);
      }

      // --- Rute yang WAJIB org aktif tervalidasi ---
      const orgCtx = await getActiveOrgId(request, env, user.id);
      if (orgCtx.error) return errorResponse(orgCtx.error, 403);
      const orgId = orgCtx.orgId;

      if (path.startsWith('/api/akun')) return await handleAkun(request, env, path, orgId, user);
      if (path.startsWith('/api/kategori')) return await handleKategori(request, env, path, orgId);
      if (path.startsWith('/api/anggota')) return await handleAnggota(request, env, path, orgId, user);
      if (path.startsWith('/api/iuran')) return await handleIuran(request, env, path, orgId, user);
      if (path.startsWith('/api/transaksi')) return await handleTransaksi(request, env, path, orgId, user);
      if (path.startsWith('/api/dashboard')) return await handleDashboard(request, env, path, orgId, user);
      if (path.startsWith('/api/laporan')) return await handleLaporan(request, env, path, orgId, user);
      if (path.startsWith('/api/notifikasi')) return await handleNotifikasi(request, env, path, orgId, user);

      return errorResponse('Endpoint tidak ditemukan', 404);
    } catch (err) {
      return errorResponse(`Terjadi kesalahan server: ${err.message}`, 500);
    }
  },
};
