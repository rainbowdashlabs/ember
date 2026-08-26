/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */

/*
 * The smallest service worker that makes Ember installable.
 *
 * A browser offers to install a site only when one of these is registered and handles requests, so
 * this exists for that offer and for nothing else. It caches nothing and serves nothing of its own:
 * every request is left to the network exactly as it would be without a worker, because an
 * application whose whole point is showing what a station decided ten seconds ago must not answer
 * from a copy made yesterday.
 */

self.addEventListener('install', () => self.skipWaiting())

self.addEventListener('activate', event => event.waitUntil(self.clients.claim()))

// Registering the handler is what counts. Answering nothing leaves the request to the network.
self.addEventListener('fetch', () => {})
