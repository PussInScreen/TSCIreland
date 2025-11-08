# TSC Ireland — Mobile PWA Wrapper

A lightweight Android & iOS project that delivers www.tscireland.org as a Progressive Web App (PWA) with offline caching. This repository contains the native shell and guidance to run the site as a cached PWA on mobile devices.

## Overview
- Purpose: Provide an Android/iOS app that loads and caches the public site for offline use.
- Approach: Serve the website in a WebView/native wrapper or install the PWA on the device using standard PWA tooling (Service Worker + manifest).

## Features
- Loads www.tscireland.org
- Offline caching via Service Worker
- Web App Manifest for installability
- Simple native wrappers for Android and iOS (optional)

## Getting started (quick)
Requirements:
- Android Studio (for Android)
- Xcode (for iOS)
- Node.js + npm (for PWA tooling / building service worker)
Optional:
- Capacitor / Cordova / PWABuilder if you want a packaged native app

## Project structure (suggested)
- android/        — Android Studio project or wrapper
- ios/            — Xcode project or wrapper
- web/            — PWA assets (service-worker.js, manifest.json)
- README.md

## Example service-worker.js (basic cache-first)
```js
const CACHE = 'tsc-cache-v1';
const URLS_TO_CACHE = [
    '/',
    '/index.html',
    // add any critical assets or routes you want cached
];

self.addEventListener('install', e => {
    e.waitUntil(caches.open(CACHE).then(c => c.addAll(URLS_TO_CACHE)));
    self.skipWaiting();
});

self.addEventListener('fetch', e => {
    e.respondWith(
        caches.match(e.request).then(cached =>
            cached || fetch(e.request).then(res => {
                return caches.open(CACHE).then(cache => {
                    // Optionally cache non-opaque responses
                    try { cache.put(e.request, res.clone()); } catch (err) {}
                    return res;
                });
            }).catch(() => caches.match('/offline.html'))
        )
    );
});
```

## Web App Manifest (manifest.json) example
```json
{
    "name": "TSC Ireland",
    "short_name": "TSC",
    "start_url": "/",
    "display": "standalone",
    "background_color": "#ffffff",
    "theme_color": "#2d6cdf",
    "icons": [
        { "src": "/icons/icon-192.png", "sizes": "192x192", "type": "image/png" },
        { "src": "/icons/icon-512.png", "sizes": "512x512", "type": "image/png" }
    ]
}
```

## Running & testing
- PWA:
    1. Serve `web/` folder (e.g., `npx http-server web`).
    2. Use Chrome DevTools Application panel to register service worker and test offline.
- Android:
    - Open android/ in Android Studio, ensure the WebView or packaged PWA points to the site or local assets, build & run.
- iOS:
    - Open ios/ in Xcode, configure WKWebView or Packaged PWA settings, build & run.

## Packaging options
- Capacitor: Use to wrap the PWA into native projects quickly.
- PWABuilder: Automates generation of platform-specific wrappers.
- Native WebView: Implement custom WebView/WKWebView with caching policies.

## Security & privacy
- Respect the upstream website's content and CORS policies.
- Only cache content that you are authorized to cache.
- Use HTTPS for site assets.

## Contributing
- Fork → branch → PR
- Describe changes and include testing steps (Android/iOS/PWA).

## License
GPLv3

## Contact
Project: TSC Ireland — info@tscireland.org
