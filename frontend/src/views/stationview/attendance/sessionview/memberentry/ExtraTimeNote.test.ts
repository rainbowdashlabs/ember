/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {defineComponent, h, ref} from 'vue'
import ExtraTimeNote from './ExtraTimeNote.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {provideSheetExtraTime} from '../useSheetExtraTime'
import type {ExtraTimeCredit} from '@/api/generated/schema'

const listExtraTime = vi.fn<(sessionId: number) => Promise<ExtraTimeCredit[]>>()
const setExtraTimeSkipped = vi.fn<(entryId: number, skip: boolean) => Promise<unknown>>()

vi.mock('@/api', () => ({
    attendance: {
        listExtraTime: (sessionId: number) => listExtraTime(sessionId),
        setExtraTimeSkipped: (entryId: number, skip: boolean) => setExtraTimeSkipped(entryId, skip),
    },
}))

function credit(overrides: Partial<ExtraTimeCredit> = {}): ExtraTimeCredit {
    return {entryId: 5, memberId: 1, preparationMinutes: 30, followUpMinutes: 15, skipped: false, ...overrides}
}

async function sheet(entryId: number, canEdit = true) {
    const wrapper = mount(defineComponent({
        setup() {
            const state = provideSheetExtraTime(ref(9), ref(canEdit))
            void state.reload()
            return () => h(ExtraTimeNote, {entryId})
        },
    }))
    await flushPromises()
    return wrapper
}

/** What a present person's row says about the time around the appointment, and how it is taken back. */
describe('ExtraTimeNote', () => {
    beforeEach(() => {
        listExtraTime.mockReset()
        setExtraTimeSkipped.mockReset()
        setExtraTimeSkipped.mockResolvedValue({})
    })

    it('names the minutes the rules credit', async () => {
        listExtraTime.mockResolvedValue([credit()])

        const wrapper = await sheet(5)

        expect(wrapper.text()).toContain('+30 Min. Vorbereitung, +15 Min. Nachbereitung')
        expect(wrapper.find('[data-testid="extra-time-toggle"]').text()).toBe('Nicht gemacht')
    })

    it('says nothing for somebody the rules do not name', async () => {
        listExtraTime.mockResolvedValue([credit()])

        const wrapper = await sheet(6)

        expect(wrapper.find('[data-testid="extra-time-note"]').exists()).toBe(false)
    })

    it('takes the minutes away and reads the sheet again', async () => {
        listExtraTime.mockResolvedValueOnce([credit()]).mockResolvedValueOnce([credit({skipped: true})])
        const wrapper = await sheet(5)

        await wrapper.find('[data-testid="extra-time-toggle"]').trigger('click')
        await flushPromises()

        expect(setExtraTimeSkipped).toHaveBeenCalledWith(5, true)
        expect(wrapper.text()).toContain('nicht gemacht')
        expect(wrapper.find('[data-testid="extra-time-toggle"]').text()).toBe('Doch gemacht')
    })

    it('offers no switch on a sheet the reader may not change', async () => {
        listExtraTime.mockResolvedValue([credit()])

        const wrapper = await sheet(5, false)

        expect(wrapper.find('[data-testid="extra-time-toggle"]').exists()).toBe(false)
    })

    it('says on the row itself when the switch could not be written', async () => {
        listExtraTime.mockResolvedValue([credit()])
        setExtraTimeSkipped.mockRejectedValue(new Error('offline'))
        const wrapper = await sheet(5)

        await wrapper.find('[data-testid="extra-time-toggle"]').trigger('click')
        await flushPromises()

        expect(wrapper.find('[data-testid="extra-time-note"]').findComponent(FailureAlert).find('p').exists()).toBe(true)
        expect(wrapper.find('[data-testid="extra-time-toggle"]').attributes('disabled')).toBeUndefined()
    })

    it('keeps the minutes it showed when reading them again fails', async () => {
        listExtraTime.mockResolvedValueOnce([credit()]).mockRejectedValueOnce(new Error('offline'))
        const wrapper = await sheet(5)

        await wrapper.find('[data-testid="extra-time-toggle"]').trigger('click')
        await flushPromises()

        expect(wrapper.text()).toContain('+30 Min. Vorbereitung')
    })
})
