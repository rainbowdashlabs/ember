/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {enableAutoUnmount, mount} from '@vue/test-utils'
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import type {ReportedMessageResponse} from '@/api/generated/schema'
import {followModifierKeys} from '@/util/modifierKeys'
import ReportCard from './ReportCard.vue'

const removeReported = vi.fn()
vi.mock('@/api/messages', () => ({
    dismissReport: vi.fn(),
    removeReported: (id: number) => removeReported(id),
}))

const REPORT = {
    id: 4, chatName: 'Zug 1', reporter: null, reportedAt: '2026-11-01T08:00:00Z', reason: null,
    reportedText: 'Hallo', messageId: 9, context: [],
} as unknown as ReportedMessageResponse

function card(onHandled: () => void = () => {}) {
    return mount(ReportCard, {props: {report: REPORT, onHandled}, attachTo: document.body})
}

/** Removing a reported message asks first, and shift skips the question. */
describe('ReportCard', () => {
    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        removeReported.mockReset().mockResolvedValue(undefined)
    })
    enableAutoUnmount(afterEach)
    afterEach(() => {
        window.dispatchEvent(new KeyboardEvent('keyup', {key: 'Shift'}))
    })

    it('asks before removing the message and removes it once agreed', async () => {
        const handled = vi.fn()
        const wrapper = card(handled)

        await wrapper.find('[data-testid="message-report-remove-4"]').trigger('click')

        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(true)
        expect(removeReported).not.toHaveBeenCalled()

        wrapper.findComponent({name: 'ConfirmDeleteModal'}).vm.$emit('confirm')
        await vi.waitFor(() => expect(handled).toHaveBeenCalledOnce())
        expect(removeReported).toHaveBeenCalledWith(4)
    })

    it('removes at once while shift is held', async () => {
        const handled = vi.fn()
        const wrapper = card(handled)

        await wrapper.find('[data-testid="message-report-remove-4"]').trigger('click', {shiftKey: true})

        await vi.waitFor(() => expect(handled).toHaveBeenCalledOnce())
        expect(removeReported).toHaveBeenCalledWith(4)
        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(false)
    })
})
