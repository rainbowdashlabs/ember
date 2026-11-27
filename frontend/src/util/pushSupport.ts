/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {translator} from '@/util/translatorState'
import {BROWSER_NAMES, browserOf, SYSTEM_NAMES, systemOf} from '@/util/userAgent'

/**
 * Where this browser stands with push messages, as the device card shows it.
 *
 * - `unsupported`: the browser cannot receive push messages at all.
 * - `installFirst`: an iPhone or iPad, where Safari delivers pushes only to a site added to the
 *   home screen and opened from there.
 * - `blocked`: the browser was set to refuse notifications from this site; only its settings change that.
 * - `ask`: nothing decided yet, so a press can ask.
 * - `allowed`: notifications are allowed; whether this browser is registered is a separate question.
 */
export type PushState = 'unsupported' | 'installFirst' | 'blocked' | 'ask' | 'allowed'

/** Whether this is an iPhone or iPad, including an iPad that reports itself as a Mac. */
export function isAppleMobile(): boolean {
    const agent = navigator.userAgent
    return /iPhone|iPad|iPod/.test(agent) || (/Macintosh/.test(agent) && navigator.maxTouchPoints > 1)
}

/** Whether Ember runs as an app from the home screen rather than in a browser tab. */
export function runsInstalled(): boolean {
    const standalone = (navigator as Navigator & {standalone?: boolean}).standalone === true
    return standalone || window.matchMedia?.('(display-mode: standalone)').matches === true
}

/** Where this browser stands. */
export function pushState(): PushState {
    if (isAppleMobile() && !runsInstalled()) return 'installFirst'
    if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) {
        return 'unsupported'
    }
    if (Notification.permission === 'denied') return 'blocked'
    if (Notification.permission === 'granted') return 'allowed'
    return 'ask'
}

/**
 * A short name of browser and system the owner recognises in the list of devices, such as
 * "Firefox auf Android". Read from the browser's own description, so it is a guess, and a plain
 * "Browser" where nothing fits.
 */
export function deviceLabel(agent = navigator.userAgent): string {
    const browser = browserOf(agent)
    const system = systemOf(agent)
    const named = browser === 'unknown' ? 'Browser' : BROWSER_NAMES[browser]
    if (system === 'unknown') return named
    return translator.t('pushDevices.deviceOn', {browser: named, system: SYSTEM_NAMES[system]})
}

/** The key as the push manager takes it. */
function keyBytes(base64url: string): ArrayBuffer {
    const padded = base64url.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (base64url.length % 4)) % 4)
    const raw = atob(padded)
    const bytes = new Uint8Array(new ArrayBuffer(raw.length))
    for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i)
    return bytes.buffer
}

/** A key the server takes back, from the bytes the push manager hands out. */
function base64url(buffer: ArrayBuffer | null): string {
    if (!buffer) return ''
    const text = btoa(String.fromCharCode(...new Uint8Array(buffer)))
    return text.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** What the browser's push manager hands over, in the shape Ember registers. */
export interface BrowserSubscription {
    endpoint: string
    p256dh: string
    auth: string
}

/**
 * Asks the browser for permission, where it has not been given yet, and subscribes it with the
 * installation's key. Must run from a click: browsers refuse to ask otherwise.
 *
 * @return the subscription, or null when the reader declined
 */
export async function subscribeBrowser(publicKey: string): Promise<BrowserSubscription | null> {
    if (Notification.permission !== 'granted') {
        const answer = await Notification.requestPermission()
        if (answer !== 'granted') return null
    }
    const registration = await workerRegistration()
    const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: keyBytes(publicKey),
    })
    return {
        endpoint: subscription.endpoint,
        p256dh: base64url(subscription.getKey('p256dh')),
        auth: base64url(subscription.getKey('auth')),
    }
}

/** The subscription this browser still holds for Ember, without asking anything; null where it has none. */
export async function currentBrowserSubscription(): Promise<BrowserSubscription | null> {
    if (!('serviceWorker' in navigator)) return null
    const registration = await navigator.serviceWorker.getRegistration()
    const subscription = await registration?.pushManager.getSubscription()
    if (!subscription) return null
    return {
        endpoint: subscription.endpoint,
        p256dh: base64url(subscription.getKey('p256dh')),
        auth: base64url(subscription.getKey('auth')),
    }
}

/**
 * The service worker that receives pushes, registered here if the page has none yet: the install
 * plugin registers it once the page has loaded, which a quick press can come before.
 */
async function workerRegistration(): Promise<ServiceWorkerRegistration> {
    const existing = await navigator.serviceWorker.getRegistration()
    if (!existing) await navigator.serviceWorker.register('/sw.js')
    return navigator.serviceWorker.ready
}

/** Ends this browser's subscription, so the push service stops taking pushes for it. */
export async function unsubscribeBrowser(): Promise<void> {
    if (!('serviceWorker' in navigator)) return
    const registration = await navigator.serviceWorker.getRegistration()
    const subscription = await registration?.pushManager.getSubscription()
    await subscription?.unsubscribe()
}
