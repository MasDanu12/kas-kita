// Kas Kita - Service Worker
// NAIKKAN VERSI INI SETIAP KALI DEPLOY supaya cache lama otomatis dibuang dan pengguna dapat versi terbaru.
const CACHE_VERSION = 'v2';
const CACHE_NAME = 'kas-kita-' + CACHE_VERSION;
const SHELL_FILES = ['/manifest.json', '/icon.png'];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_FILES)).catch(() => {}));
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))))
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // API: jangan pernah cache - data harus selalu segar dan sesuai organisasi aktif
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(fetch(event.request).catch(() => new Response(JSON.stringify({ error: 'Offline' }), { status: 503 })));
    return;
  }

  // Shell HTML (index.html / '/'): network-first, supaya update kode langsung terpakai
  // dan tidak tersangkut versi lama di cache. Fallback ke cache hanya kalau offline.
  if (url.pathname === '/' || url.pathname === '/index.html') {
    event.respondWith(
      fetch(event.request).then((res) => {
        const clone = res.clone();
        caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
        return res;
      }).catch(() => caches.match(event.request))
    );
    return;
  }

  // Aset statis lain (manifest, icon, sw sendiri): cache-first, fallback ke network
  event.respondWith(
    caches.match(event.request).then((cached) => cached || fetch(event.request).then((res) => {
      const clone = res.clone();
      caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
      return res;
    }).catch(() => cached))
  );
});
