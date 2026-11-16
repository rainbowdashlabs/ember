/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import PastView from './PastView.vue'

/**
 * A sheet filled in weeks after the appointment it records: the appointment ran on the sixth of July and
 * somebody wrote it down on the fourteenth of September. Those are the only rows where the two dates
 * disagree, and the list showed the wrong one of them for every such row.
 */
const WRITTEN_DOWN_LATE = {
    id: 12,
    templateId: 9,
    title: 'Dienstabend',
    startTime: '2026-07-06T15:30:00Z',
    endTime: '2026-07-06T17:30:00Z',
    createdAt: '2026-09-14T18:27:19Z',
    eventId: null,
    presentCount: 4,
    absentCount: 1,
    declinedCount: 0,
    unconfirmedCount: 0,
}

const listSessionSummaries = vi.fn()

vi.mock('@/api', () => ({
    attendance: {listSessionSummaries: (...args: unknown[]) => listSessionSummaries(...args)},
}))

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({loaded: {value: true}}),
}))

describe('PastView', () => {
    beforeEach(() => {
        listSessionSummaries.mockReset()
        listSessionSummaries.mockResolvedValue([WRITTEN_DOWN_LATE])
    })

    it('dates an appointment by when it was, not by when somebody wrote it down', async () => {
        const wrapper = await mountSuspended(PastView)
        await new Promise(resolve => setTimeout(resolve, 0))
        await wrapper.vm.$nextTick()

        const text = wrapper.text()
        expect(text).toContain('06.07.2026')
        expect(text).not.toContain('14.09.2026')
    })

    it('keeps the times of the appointment beside its date', async () => {
        const wrapper = await mountSuspended(PastView)
        await new Promise(resolve => setTimeout(resolve, 0))
        await wrapper.vm.$nextTick()

        const text = wrapper.text()
        expect(text).toContain('06.07.2026')
        expect(text).toMatch(/1[57]:30/)
    })

    it('asks for the first page of 25 when nothing is searched', async () => {
        await mountSuspended(PastView)
        await new Promise(resolve => setTimeout(resolve, 0))

        expect(listSessionSummaries).toHaveBeenCalledWith({search: undefined, limit: 25, offset: 0})
    })

    it('loads the next page behind the ones it shows when more are waiting', async () => {
        const page = Array.from({length: 25}, (_, i) => ({...WRITTEN_DOWN_LATE, id: 100 + i}))
        listSessionSummaries.mockResolvedValueOnce(page).mockResolvedValueOnce([WRITTEN_DOWN_LATE])
        const wrapper = await mountSuspended(PastView)
        await new Promise(resolve => setTimeout(resolve, 0))
        await wrapper.vm.$nextTick()

        await wrapper.get('[data-testid="attendance-past-more"]').trigger('click')
        await new Promise(resolve => setTimeout(resolve, 0))
        await wrapper.vm.$nextTick()

        expect(listSessionSummaries).toHaveBeenLastCalledWith({search: undefined, limit: 25, offset: 25})
        expect(wrapper.findAll('[data-testid="attendance-session"]')).toHaveLength(26)
        expect(wrapper.find('[data-testid="attendance-past-more"]').exists(), 'a short page was the last').toBe(false)
    })

    it('searches from the first page again and says when nothing matches', async () => {
        vi.useFakeTimers()
        try {
            const wrapper = await mountSuspended(PastView)
            await vi.runAllTimersAsync()
            listSessionSummaries.mockResolvedValue([])

            await wrapper.get('[data-testid="attendance-past-search"] input').setValue('  Jugend ')
            await vi.advanceTimersByTimeAsync(400)
            await wrapper.vm.$nextTick()

            expect(listSessionSummaries).toHaveBeenLastCalledWith({search: 'Jugend', limit: 25, offset: 0})
            expect(wrapper.text()).toContain('Keine Anwesenheit passt zur Suche.')
        } finally {
            vi.useRealTimers()
        }
    })
})
