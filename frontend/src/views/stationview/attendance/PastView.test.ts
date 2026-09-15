/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import PastView from './PastView.vue'

/**
 * A sheet filled in weeks after the evening it records: the evening ran on the sixth of July and
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

    it('dates an evening by when it was, not by when somebody wrote it down', async () => {
        const wrapper = await mountSuspended(PastView)
        await new Promise(resolve => setTimeout(resolve, 0))
        await wrapper.vm.$nextTick()

        const text = wrapper.text()
        expect(text).toContain('06.07.2026')
        expect(text).not.toContain('14.09.2026')
    })

    it('keeps the times of the evening beside its date', async () => {
        const wrapper = await mountSuspended(PastView)
        await new Promise(resolve => setTimeout(resolve, 0))
        await wrapper.vm.$nextTick()

        const text = wrapper.text()
        expect(text).toContain('06.07.2026')
        expect(text).toMatch(/1[57]:30/)
    })
})
