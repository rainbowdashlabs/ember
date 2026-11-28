/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import PushDeviceCard from './PushDeviceCard.vue'
import type ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const api = vi.hoisted(() => ({
    listPushDevices: vi.fn(),
    registerPushDevice: vi.fn(),
    removePushDevice: vi.fn(),
    testPushDevice: vi.fn(),
}))
const browser = vi.hoisted(() => ({
    pushState: vi.fn(),
    subscribeBrowser: vi.fn(),
    unsubscribeBrowser: vi.fn(),
    currentBrowserSubscription: vi.fn(),
    deviceLabel: () => 'Firefox auf Android',
}))

const messages = vi.hoisted(() => ({
    getStatus: vi.fn(),
    updateSettings: vi.fn(),
}))

vi.mock('@/api/push', () => api)
vi.mock('@/api/messages', () => messages)
vi.mock('@/util/pushSupport', () => browser)

const THIS_DEVICE = {id: 4, label: 'Firefox auf Android', createdAt: '2026-11-17T08:00:00Z', lastSuccessAt: null, current: true}

async function card() {
    const wrapper = await mountSuspended(PushDeviceCard, {props: {publicKey: 'BKey'}})
    await flushPromises()
    return wrapper
}

/**
 * The card asks nothing of the browser until pressed, then registers this device under its name, and
 * shows plainly where the browser cannot do it.
 */
describe('PushDeviceCard', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        browser.pushState.mockReturnValue('ask')
        api.listPushDevices.mockResolvedValue([])
        messages.getStatus.mockResolvedValue({available: true, unread: 0, mail: true, stationDigest: 'MENTIONS', push: true})
    })

    it('registers this device after the press and lists it', async () => {
        browser.subscribeBrowser.mockResolvedValue({endpoint: 'https://push.example/a', p256dh: 'p', auth: 'a'})
        api.listPushDevices.mockResolvedValueOnce([]).mockResolvedValueOnce([THIS_DEVICE])
        const wrapper = await card()
        expect(browser.subscribeBrowser).not.toHaveBeenCalled()

        await wrapper.get('[data-testid="push-enable"]').trigger('click')
        await flushPromises()

        expect(browser.subscribeBrowser).toHaveBeenCalledWith('BKey')
        expect(api.registerPushDevice).toHaveBeenCalledWith(
            {endpoint: 'https://push.example/a', p256dh: 'p', auth: 'a', label: 'Firefox auf Android'})
        expect(wrapper.findAll('[data-testid="push-device"]')).toHaveLength(1)
        expect(wrapper.find('[data-testid="push-enable"]').exists(), 'this device is on').toBe(false)
    })

    it('registers nothing when the reader declines', async () => {
        browser.subscribeBrowser.mockResolvedValue(null)
        const wrapper = await card()

        await wrapper.get('[data-testid="push-enable"]').trigger('click')
        await flushPromises()

        expect(api.registerPushDevice).not.toHaveBeenCalled()
    })

    it('explains the home screen on an iPhone and how to unblock a browser that refused', async () => {
        browser.pushState.mockReturnValue('installFirst')
        expect((await card()).find('[data-testid="push-install-first"]').exists()).toBe(true)

        browser.pushState.mockReturnValue('blocked')
        const blocked = await card()
        expect(blocked.find('[data-testid="push-blocked"]').exists()).toBe(true)
        expect(blocked.find('[data-testid="push-enable"]').exists()).toBe(false)
    })

    it('removes this device and lets the browser go of it', async () => {
        api.listPushDevices.mockResolvedValueOnce([THIS_DEVICE]).mockResolvedValueOnce([])
        const wrapper = await card()

        await wrapper.get('[data-testid="push-remove"]').trigger('click')
        await flushPromises()

        expect(browser.unsubscribeBrowser).toHaveBeenCalled()
        expect(api.removePushDevice).toHaveBeenCalledWith(4)
    })

    it('registers a browser again without asking once its sign-in was renewed', async () => {
        browser.pushState.mockReturnValue('allowed')
        browser.currentBrowserSubscription.mockResolvedValue({endpoint: 'https://push.example/b', p256dh: 'p', auth: 'a'})
        api.listPushDevices.mockResolvedValueOnce([]).mockResolvedValueOnce([THIS_DEVICE])

        await card()

        expect(browser.subscribeBrowser).not.toHaveBeenCalled()
        expect(api.registerPushDevice).toHaveBeenCalledWith(
            {endpoint: 'https://push.example/b', p256dh: 'p', auth: 'a', label: 'Firefox auf Android', renewal: true, previousEndpoint: null})
    })

    it('switches message pushes for the whole account, and hides the switch without messages', async () => {
        messages.updateSettings.mockResolvedValue({available: true, unread: 0, mail: true, stationDigest: 'MENTIONS', push: false})
        const wrapper = await card()

        wrapper.findComponent<typeof ToggleInput>('[data-testid="push-messages-toggle"]').vm.$emit('update:modelValue', false)
        await flushPromises()
        expect(messages.updateSettings).toHaveBeenCalledWith({push: false})

        messages.getStatus.mockResolvedValue({available: false, unread: 0, mail: true, stationDigest: 'MENTIONS', push: true})
        expect((await card()).find('[data-testid="push-messages-toggle"]').exists()).toBe(false)
    })
})
