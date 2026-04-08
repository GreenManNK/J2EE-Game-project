const SHELL_CACHE = 'gamehub-shell-v4';
const ASSET_CACHE = 'gamehub-assets-v4';
const STATIC_ASSETS = [
  './',
  './manifest.webmanifest',
  './icons/favicon-32.png',
  './icons/favicon-16.png',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-180.png',
  './images/brand/gamehub-logo.png',
  './css/site.css',
  './css/games-portal.css',
  './css/layout-refresh.css?v=20260307a',
  './css/cg-market.css?v=20260315b',
  './css/game-identity.css?v=20260304a',
  './css/arcade-shell.css?v=20260313a',
  './css/account-surface.css?v=20260313a',
  './css/unified-app.css?v=20260315a',
  './css/play-surfaces.css?v=20260315a',
  './js/site.js',
  './js/app-shell.js',
  './js/install-app.js',
  './js/private-chat.js?v=20260327b',
  './vendor/sockjs.min.js',
  './vendor/stomp.umd.min.js'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(SHELL_CACHE)
      .then((cache) => cache.addAll(STATIC_ASSETS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(
      keys
        .filter((key) => key !== SHELL_CACHE && key !== ASSET_CACHE)
        .map((key) => caches.delete(key))
    )).then(() => self.clients.claim())
  );
});

function shouldHandleAsset(request, url) {
  if (request.method !== 'GET' || url.origin !== self.location.origin) {
    return false;
  }
  if (request.mode === 'navigate') {
    return true;
  }
  if (url.pathname.includes('/api/') || url.pathname.startsWith('/ws')) {
    return false;
  }
  return ['style', 'script', 'image', 'font', 'manifest'].includes(request.destination);
}

async function handleNavigate(request) {
  const cache = await caches.open(SHELL_CACHE);
  try {
    const response = await fetch(request);
    cache.put('./', response.clone());
    return response;
  } catch (_) {
    const cached = await cache.match(request);
    if (cached) {
      return cached;
    }
    return cache.match('./');
  }
}

async function handleAsset(request) {
  const cache = await caches.open(ASSET_CACHE);
  const cached = await cache.match(request);
  if (cached) {
    fetch(request)
      .then((response) => {
        if (response && response.ok) {
          cache.put(request, response.clone());
        }
      })
      .catch(() => {});
    return cached;
  }

  const response = await fetch(request);
  if (response && response.ok) {
    cache.put(request, response.clone());
  }
  return response;
}

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (!shouldHandleAsset(event.request, url)) {
    return;
  }

  if (event.request.mode === 'navigate') {
    event.respondWith(handleNavigate(event.request));
    return;
  }

  event.respondWith(handleAsset(event.request));
});
