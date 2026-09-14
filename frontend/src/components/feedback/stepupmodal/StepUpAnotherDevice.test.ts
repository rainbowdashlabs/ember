/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import StepUpAnotherDevice from './StepUpAnotherDevice.vue'

const stepUpDeviceBegin = vi.fn()
const stepUpDevicePoll = vi.fn()

vi.mock('@/api', () => ({
    passkeys: {
        stepUpDeviceBegin: (...args: unknown[]) => stepUpDeviceBegin(...args),
        stepUpDevicePoll: (...args: unknown[]) => stepUpDevicePoll(...args),
    },
}))

/**
 * Confirming a sensitive action on a device the reader already holds. What matters is that the code
 * reaches the screen, that the waiting ends when the other device answers, and that a dead request
 * says so rather than spinning for ever.
 */
function mountPanel() {
    return mount(StepUpAnotherDevice, {
        props: {category: 'ACCOUNT_SECURITY'},
        global: {stubs: {Spinner: true}},
    })
}

describe('StepUpAnotherDevice', () => {
    beforeEach(() => {
        vi.useFakeTimers()
        stepUpDeviceBegin.mockResolvedValue({code: 'K7RM2WQD', pollSecret: 'secret', expiresAt: ''})
        stepUpDevicePoll.mockResolvedValue({status: 'PENDING'})
    })

    afterEach(() => {
        vi.useRealTimers()
        vi.clearAllMocks()
    })

    it('shows the code in fours once the request is raised', async () => {
        const wrapper = mountPanel()
        await wrapper.get('button').trigger('click')
        await flushPromises()

        expect(wrapper.get('[data-testid="stepup-device-code"]').text()).toBe('K7RM-2WQD')
    })

    it('tells the server what is being confirmed, so the other device can say it', async () => {
        const wrapper = mountPanel()
        await wrapper.get('button').trigger('click')
        await flushPromises()

        expect(stepUpDeviceBegin).toHaveBeenCalledWith('ACCOUNT_SECURITY', null)
    })

    it('reports the confirmation once the other device answers', async () => {
        const wrapper = mountPanel()
        await wrapper.get('button').trigger('click')
        await flushPromises()

        stepUpDevicePoll.mockResolvedValue({status: 'CONFIRMED'})
        await vi.advanceTimersByTimeAsync(2500)
        await flushPromises()

        expect(wrapper.emitted('confirmed')).toHaveLength(1)
    })

    it('stops polling once it has reported, so one answer is reported once', async () => {
        const wrapper = mountPanel()
        await wrapper.get('button').trigger('click')
        await flushPromises()

        stepUpDevicePoll.mockResolvedValue({status: 'CONFIRMED'})
        await vi.advanceTimersByTimeAsync(2500)
        await flushPromises()
        await vi.advanceTimersByTimeAsync(10000)
        await flushPromises()

        expect(wrapper.emitted('confirmed')).toHaveLength(1)
    })

    it('says a dead request is dead rather than waiting for ever', async () => {
        const wrapper = mountPanel()
        await wrapper.get('button').trigger('click')
        await flushPromises()

        stepUpDevicePoll.mockResolvedValue({status: 'EXPIRED'})
        await vi.advanceTimersByTimeAsync(2500)
        await flushPromises()

        expect(wrapper.find('[data-testid="stepup-device-code"]').exists()).toBe(false)
        expect(wrapper.text()).toContain('abgelaufen')
    })

    it('keeps waiting when a single poll is lost', async () => {
        const wrapper = mountPanel()
        await wrapper.get('button').trigger('click')
        await flushPromises()

        stepUpDevicePoll.mockRejectedValueOnce(new Error('offline'))
        await vi.advanceTimersByTimeAsync(2500)
        await flushPromises()

        expect(wrapper.get('[data-testid="stepup-device-code"]').text()).toBe('K7RM-2WQD')
        expect(wrapper.emitted('confirmed')).toBeUndefined()
    })
})
