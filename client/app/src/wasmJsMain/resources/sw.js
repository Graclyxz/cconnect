const CACHE = 'cconnect-shell-v1';

self.addEventListener('install', function () {
    self.skipWaiting();
});

self.addEventListener('activate', function (event) {
    event.waitUntil(self.clients.claim());
});

// Network-first for the app shell (same-origin GET): online always serves fresh
// and refreshes the cache; offline falls back to the cached copy so the app still
// loads (theme, navigation). Backend/API requests are cross-origin and pass through.
self.addEventListener('fetch', function (event) {
    const request = event.request;
    if (request.method !== 'GET') return;
    if (new URL(request.url).origin !== self.location.origin) return;
    event.respondWith(
        fetch(request)
            .then(function (response) {
                const copy = response.clone();
                caches.open(CACHE).then(function (cache) {
                    cache.put(request, copy);
                });
                return response;
            })
            .catch(function () {
                return caches.match(request).then(function (cached) {
                    return cached || caches.match('index.html');
                });
            })
    );
});
