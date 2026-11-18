/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {test, expect} from './fixtures/auth'

/**
 * Push notifications are switched on for a device from the notification settings, chosen per kind,
 * tested and removed again, and the device shows among the sign-ins.
 *
 * A real push needs the browser maker's push service, which a test run cannot reach, so the browser's
 * push manager is stood in for: it hands out a subscription with a freshly made P-256 key, as a real
 * one does, to an address that never resolves, so a test push is only ever tried again and nothing
 * leaves the test network. Like a real one, the subscription outlives a page load, so the page does not
 * register the browser anew under another address each time it opens. A headless browser reports notifications as refused whatever it was
 * granted, so the permission is stood in for too. Whether a push then arrives on a device is checked by hand.
 */
test.describe('Push notifications', () => {
    test.use({serviceWorkers: 'allow'})

    test.beforeEach(async ({memberPage: page}) => {
        await page.context().grantPermissions(['notifications'])
        await page.addInitScript(() => {
            Object.defineProperty(Notification, 'permission', {get: () => 'granted'})
            Notification.requestPermission = async () => 'granted'
            const KEPT = 'e2e-push-subscription'
            type Kept = {endpoint: string, p256dh: number[], auth: number[]}
            function asSubscription(kept: Kept): PushSubscription {
                return {
                    endpoint: kept.endpoint,
                    getKey: (name: string) => new Uint8Array(name === 'p256dh' ? kept.p256dh : kept.auth).buffer,
                    unsubscribe: async () => { sessionStorage.removeItem(KEPT); return true },
                } as unknown as PushSubscription
            }
            PushManager.prototype.subscribe = async function () {
                const pair = await crypto.subtle.generateKey({name: 'ECDH', namedCurve: 'P-256'}, true, ['deriveBits'])
                const kept: Kept = {
                    endpoint: `https://push.invalid/e2e-${crypto.randomUUID()}`,
                    p256dh: [...new Uint8Array(await crypto.subtle.exportKey('raw', pair.publicKey))],
                    auth: [...crypto.getRandomValues(new Uint8Array(16))],
                }
                sessionStorage.setItem(KEPT, JSON.stringify(kept))
                return asSubscription(kept)
            }
            PushManager.prototype.getSubscription = async function () {
                const kept = sessionStorage.getItem(KEPT)
                return kept ? asSubscription(JSON.parse(kept) as Kept) : null
            }
        })
    })

    test('a device is switched on, tested, listed among the sign-ins and removed again', async ({memberPage: page}) => {
        await page.goto('/station/profile/settings/notifications')

        const card = page.getByTestId('push-device-card')
        await expect(card).toBeVisible()
        await card.getByTestId('push-enable').click()

        const device = card.getByTestId('push-device')
        await expect(device, 'this browser is registered').toHaveCount(1)
        await expect(device).toContainText('Dieses Gerät')
        await expect(page.getByTestId('push-toggle-NEW_NEWS'), 'the matrix gains a push column').toBeVisible()

        await device.getByTestId('push-test').click()
        await expect(card).toContainText('Test verschickt')

        await page.goto('/account/sessions')
        await expect(page.getByTestId('session-push-badge'), 'the sign-in says it receives pushes').toHaveCount(1)

        await page.goto('/station/profile/settings/notifications')
        await page.getByTestId('push-device-card').getByTestId('push-remove').click()
        await expect(page.getByTestId('push-device')).toHaveCount(0)
        await expect(page.getByTestId('push-enable'), 'it can be switched on again').toBeVisible()
    })
})
