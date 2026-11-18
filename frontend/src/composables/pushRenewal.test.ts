/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {renewPushRegistration} from './pushRenewal'

const api = vi.hoisted(() => ({registerPushDevice: vi.fn(), getPushKey: vi.fn()}))
const browser = vi.hoisted(() => ({
    pushState: vi.fn(),
    currentBrowserSubscription: vi.fn(),
    subscribeBrowser: vi.fn(),
    deviceLabel: vi.fn(() => 'Firefox auf Android'),
}))
const stored = vi.hoisted(() => new Map<string, string>())
vi.mock('@/api/push', () => api)
vi.mock('@/util/pushSupport', () => browser)
vi.mock('@/api/storage', () => ({
    getItem: (key: string) => stored.get(key) ?? null,
    setItem: (key: string, value: string) => stored.set(key, value),
    removeItem: (key: string) => stored.delete(key),
}))

const SUBSCRIPTION = {endpoint: 'https://push.example/a', p256dh: 'p', auth: 'a'}

/**
 * Renewing registers a browser that still has the permission, marked as a renewal and with the address it was
 * registered under before, subscribes anew where the browser dropped its subscription, and says nothing where
 * it cannot.
 *
 * @vitest-environment happy-dom
 */
describe('renewPushRegistration', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        stored.clear()
        browser.pushState.mockReturnValue('allowed')
        browser.currentBrowserSubscription.mockResolvedValue(SUBSCRIPTION)
        api.getPushKey.mockResolvedValue({publicKey: 'key', enabled: true})
    })

    it('registers the browser as a renewal and remembers its address', async () => {
        api.registerPushDevice.mockResolvedValue({id: 1})

        expect(await renewPushRegistration()).toBe(true)
        expect(api.registerPushDevice).toHaveBeenCalledWith(
            {...SUBSCRIPTION, label: 'Firefox auf Android', renewal: true, previousEndpoint: null})
        expect(stored.get('push_endpoint')).toBe(SUBSCRIPTION.endpoint)
    })

    it('brings the old address along once the browser moved to a new one', async () => {
        stored.set('push_endpoint', 'https://push.example/old')
        api.registerPushDevice.mockResolvedValue({id: 1})

        await renewPushRegistration()

        expect(api.registerPushDevice).toHaveBeenCalledWith(
            expect.objectContaining({endpoint: SUBSCRIPTION.endpoint, previousEndpoint: 'https://push.example/old'}))
        expect(stored.get('push_endpoint')).toBe(SUBSCRIPTION.endpoint)
    })

    it('subscribes anew where the browser dropped a subscription it had', async () => {
        stored.set('push_endpoint', 'https://push.example/old')
        browser.currentBrowserSubscription.mockResolvedValue(null)
        browser.subscribeBrowser.mockResolvedValue(SUBSCRIPTION)
        api.registerPushDevice.mockResolvedValue({id: 1})

        expect(await renewPushRegistration()).toBe(true)
        expect(browser.subscribeBrowser).toHaveBeenCalledWith('key')
    })

    it('keeps the old address where the server leaves the browser out', async () => {
        stored.set('push_endpoint', 'https://push.example/old')
        api.registerPushDevice.mockResolvedValue(null)

        expect(await renewPushRegistration()).toBe(false)
        expect(stored.get('push_endpoint')).toBe('https://push.example/old')
    })

    it('does nothing without the permission or any subscription, and swallows a failure', async () => {
        browser.pushState.mockReturnValue('default')
        expect(await renewPushRegistration()).toBe(false)

        browser.pushState.mockReturnValue('allowed')
        browser.currentBrowserSubscription.mockResolvedValue(null)
        expect(await renewPushRegistration()).toBe(false)
        expect(browser.subscribeBrowser).not.toHaveBeenCalled()

        browser.currentBrowserSubscription.mockResolvedValue(SUBSCRIPTION)
        api.registerPushDevice.mockRejectedValue(new Error('offline'))
        expect(await renewPushRegistration()).toBe(false)
        expect(api.registerPushDevice).toHaveBeenCalledTimes(1)
    })
})
