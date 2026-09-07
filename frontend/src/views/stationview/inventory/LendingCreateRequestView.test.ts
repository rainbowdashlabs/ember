/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {ref} from 'vue'
import LendingCreateRequestView from './LendingCreateRequestView.vue'

const listAvailable = vi.fn()

vi.mock('@/api/lending', () => ({
    listAvailable: (...args: unknown[]) => listAvailable(...args),
    createRequest: vi.fn(),
}))

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({loaded: ref(true)}),
}))

const query: Record<string, string> = {}

vi.mock('vue-router', async (importOriginal) => ({
    ...(await importOriginal<typeof import('vue-router')>()),
    useRoute: () => ({query}),
}))

/**
 * The period a request is made for is the one that was searched for.
 *
 * <p>The number beside a partner's inventory counts what is free in the days asked about, not what
 * the partner owns. A form that opens on no dates makes somebody type the same two again, and any
 * other pair makes the count they clicked on wrong.
 */
describe('LendingCreateRequestView', () => {
    const STATION_ID = '86d8830a-79d6-3a86-99ee-d9d8644e33b6'
    const ENTRY = {
        inventoryId: 26,
        stationId: STATION_ID,
        stationName: 'JF Partnerwache',
        inventoryName: 'Funkgeräte',
        availableCount: 4,
    }
    async function open(dates: {dateFrom?: string; dateTo?: string} = {}) {
        Object.assign(query, {inventoryId: '26', stationId: STATION_ID, stationName: ENTRY.stationName}, dates)
        return mountSuspended(LendingCreateRequestView)
    }

    function shownDates(form: Awaited<ReturnType<typeof mountSuspended>>): string[] {
        return form
            .findAll('input[type="date"]')
            .map((input: {element: Element}) => (input.element as HTMLInputElement).value)
    }

    beforeEach(() => {
        vi.clearAllMocks()
        for (const key of Object.keys(query)) delete query[key]
        listAvailable.mockResolvedValue({entries: [ENTRY]})
    })

    it('opens on the period the search was made for', async () => {
        const form = await open({dateFrom: '2026-10-12', dateTo: '2026-10-14'})

        expect(shownDates(form)).toContain('2026-10-12')
        expect(shownDates(form)).toContain('2026-10-14')
    })

    it('counts what is free for that same period rather than for no period at all', async () => {
        await open({dateFrom: '2026-10-12', dateTo: '2026-10-14'})

        expect(listAvailable).toHaveBeenCalledWith({from: '2026-10-12', to: '2026-10-14'})
    })

    it('leaves the dates empty when the search named none', async () => {
        const form = await open()

        expect(shownDates(form).every(value => value === '')).toBe(true)
        expect(listAvailable).toHaveBeenCalledWith({})
    })

    it('says nothing is free rather than falling silent when the period leaves none', async () => {
        listAvailable.mockResolvedValue({entries: []})

        const form = await open({dateFrom: '2026-10-12'})

        expect(form.text()).toContain('Nichts frei im gewählten Zeitraum')
    })
})
