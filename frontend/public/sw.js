/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */

/*
 * The service worker that makes Ember installable, and that hands a finished file to the browser as
 * a download.
 *
 * A browser offers to install a site only when one of these is registered and handles requests. It
 * caches nothing: every request is left to the network exactly as it would be without a worker,
 * because an application whose whole point is showing what a station decided ten seconds ago must
 * not answer from a copy made yesterday.
 *
 * The one thing it answers itself is a saved file. A page can only offer bytes it holds as a blob
 * address, and a browser decides what to do with one of those by guessing: Firefox reads the name,
 * finds a document it has no viewer for on a phone, and navigates to it rather than saving it. In an
 * installed app that navigation is the whole window, so the reader loses the page they were on and
 * gets no file. Answered from here the bytes arrive as an ordinary response from the network, under a
 * header that says to save them, which is the one thing every browser does the same way.
 *
 * It also shows push messages. Ember sends each as a small JSON object of title, body, the address
 * a tap opens and a tag; a push about the same thing carries the same tag and replaces the earlier
 * one on the device. A tap focuses an Ember window that is already open and leads it there, or opens
 * one. Only addresses of this installation are opened.
 */

/** Where a saved file is asked for. Matched by the page, which puts an identifier after it. */
const SAVE_PATH = '/save-file/'

/**
 * How long a file is kept. Long enough for a reader to open it, read a page and then save it from
 * the viewer it opened in, and short enough that a file nobody fetched does not sit here.
 */
const SAVE_LIFETIME_MS = 300_000

const waiting = new Map()

self.addEventListener('install', () => self.skipWaiting())

self.addEventListener('activate', event => event.waitUntil(self.clients.claim()))

self.addEventListener('message', event => {
    const message = event.data
    if (!message || message.type !== 'save-file') return
    waiting.set(message.id, {
        blob: message.blob,
        filename: message.filename,
        contentType: message.contentType || 'application/octet-stream',
        disposition: message.disposition === 'inline' ? 'inline' : 'attachment',
    })
    setTimeout(() => waiting.delete(message.id), SAVE_LIFETIME_MS)
    event.ports[0]?.postMessage({held: true})
})

self.addEventListener('fetch', event => {
    const url = new URL(event.request.url)
    if (url.origin !== self.location.origin || !url.pathname.startsWith(SAVE_PATH)) return
    event.respondWith(saved(url.pathname.slice(SAVE_PATH.length)))
})

/**
 * The file under that identifier, kept until its time is up rather than until it has been read.
 *
 * <p>A document opened in the browser's own viewer is fetched more than once: the window that opens
 * it asks for it, and saving it from there asks again. Dropped after the first read, the second ask
 * would find nothing. A worker is stopped whenever nothing is asking it anything and forgets what it
 * held then anyway, so an identifier nobody knows any more is answered rather than left hanging, and
 * the reader sees a failure instead of a request that never ends.
 */
function saved(id) {
    const file = waiting.get(id)
    if (!file) return new Response('', {status: 404})
    return new Response(file.blob, {
        headers: {
            'Content-Type': file.contentType,
            'Content-Length': String(file.blob.size),
            'Content-Disposition': `${file.disposition}; filename*=UTF-8''${encodeURIComponent(file.filename)}`,
        },
    })
}

/** What a push says when its content cannot be read, so the device still shows that something came. */
const FALLBACK_TITLE = 'Ember'
const FALLBACK_BODY = 'Neue Benachrichtigung in Ember'

self.addEventListener('push', event => {
    let message = {}
    try {
        message = event.data ? event.data.json() : {}
    } catch {
        message = {}
    }
    const title = typeof message.title === 'string' && message.title ? message.title : FALLBACK_TITLE
    const options = {
        body: typeof message.body === 'string' && message.body ? message.body : FALLBACK_BODY,
        tag: typeof message.tag === 'string' ? message.tag : undefined,
        icon: '/favicon/android-chrome-192x192.png',
        badge: '/favicon/android-chrome-192x192.png',
        data: {url: sameOrigin(message.url)},
    }
    event.waitUntil(self.registration.showNotification(title, options))
})

/**
 * The browser moved this worker's push address, or dropped it. A new subscription is taken with the old one's
 * options where the browser did not hand one over, and every open page is told, so it registers the new address
 * for whoever is signed in there. The worker cannot do that itself: the sign-in lives in the page.
 */
self.addEventListener('pushsubscriptionchange', event => {
    event.waitUntil(resubscribe(event).then(() => tellPages({type: 'push-subscription-changed'})))
})

/** Takes a new subscription with the options of the old one, unless the browser already made one. */
async function resubscribe(event) {
    if (event.newSubscription || !event.oldSubscription) return
    try {
        await self.registration.pushManager.subscribe(event.oldSubscription.options)
    } catch {
        return
    }
}

/** Sends a message to every page of Ember this browser has open. */
async function tellPages(message) {
    const pages = await self.clients.matchAll({type: 'window', includeUncontrolled: true})
    for (const page of pages) page.postMessage(message)
}

self.addEventListener('notificationclick', event => {
    event.notification.close()
    const target = sameOrigin(event.notification.data && event.notification.data.url)
    event.waitUntil(open(target))
})

/** The address if it belongs to this installation, otherwise its start page. */
function sameOrigin(url) {
    try {
        const parsed = new URL(url, self.location.origin)
        return parsed.origin === self.location.origin ? parsed.href : self.location.origin + '/'
    } catch {
        return self.location.origin + '/'
    }
}

/**
 * Leads an open Ember window to the address, or opens a new one. A window the worker does not
 * control refuses to be led, so a new one is opened instead.
 */
async function open(url) {
    const windows = await self.clients.matchAll({type: 'window', includeUncontrolled: true})
    for (const client of windows) {
        if (new URL(client.url).origin !== self.location.origin) continue
        try {
            await client.focus()
            const led = 'navigate' in client ? await client.navigate(url) : null
            if (led) return led
        } catch {
            break
        }
    }
    return self.clients.openWindow(url)
}
