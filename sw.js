// Uygulama kabuğunu önbelleğe alır; içerik listesi her açılışta ağdan tazelenir.
const SHELL = 'shell-v3';
const FILES = ['./', './index.html', './app.webmanifest', './icons/icon-192.png', './icons/apple-touch-icon.png'];
self.addEventListener('install', e => { e.waitUntil(caches.open(SHELL).then(c => c.addAll(FILES))); self.skipWaiting(); });
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== SHELL && k !== 'media').map(k => caches.delete(k)))));
  self.clients.claim();
});
self.addEventListener('fetch', e => {
  const u = new URL(e.request.url);
  if (e.request.method !== 'GET' || u.origin !== location.origin) return;
  if (u.pathname.endsWith('/content/manifest.json')) {
    e.respondWith(fetch(e.request).then(r => { const c = r.clone(); caches.open('media').then(x => x.put(e.request, c)); return r; })
      .catch(() => caches.match(e.request)));
    return;
  }
  if (u.pathname.includes('/content/')) {
    e.respondWith(caches.open('media').then(c => c.match(e.request).then(hit => hit || fetch(e.request).then(r => {
      if (r.ok && !u.pathname.endsWith('.mp4')) c.put(e.request, r.clone()); return r; }))));
    return;
  }
  e.respondWith(fetch(e.request).then(r => { const c = r.clone(); caches.open(SHELL).then(x => x.put(e.request, c)); return r; })
    .catch(() => caches.match(e.request).then(h => h || caches.match('./'))));
});
