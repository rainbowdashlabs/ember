/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {defineComponent, ref} from 'vue'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import type {SessionSummary} from '@/api/generated/schema'
import {useQuickSearchResults} from './useQuickSearchResults'

const listSessionSummaries = vi.fn()

vi.mock('@/api/attendance', () => ({
    listSessionSummaries: (...args: unknown[]) => listSessionSummaries(...args),
}))
vi.mock('@/api/events', () => ({listUpcomingOccurrences: vi.fn(async () => [])}))
vi.mock('@/api/stationMembers', () => ({listCompletions: vi.fn(async () => [])}))
vi.mock('@/api/knowledgeBase', () => ({search: vi.fn(async () => [])}))
vi.mock('@/api/inventory', () => ({listInventories: vi.fn(async () => [])}))
vi.mock('@/composables/useStationNav', () => ({useStationNav: () => ({nodes: ref([])})}))
vi.mock('@/composables/useAdminNav', () => ({useAdminNav: () => ({nodes: ref([])})}))
vi.mock('@/composables/useClusterNav', () => ({useClusterNav: () => ({nodes: ref([])})}))

let readsAttendance = true
let attendanceOn = true

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({
        hasPermission: (permission: string) => permission !== 'ATTENDANCE_READ' || readsAttendance,
        isModuleEnabled: (module: string) => module !== 'ATTENDANCE' || attendanceOn,
    }),
}))

const SHEET: SessionSummary = {
    id: 41,
    templateId: 3,
    title: 'Dienstabend Atemschutz',
    startTime: '2026-11-10T17:00:00Z',
    endTime: '2026-11-10T19:00:00Z',
    createdAt: '2026-11-10T17:05:00Z',
    eventId: null,
    presentCount: 8,
    absentCount: 1,
    declinedCount: 0,
    unconfirmedCount: 0,
}

async function palette() {
    const query = ref('')
    let results!: ReturnType<typeof useQuickSearchResults>
    await mountSuspended(defineComponent({
        setup() {
            results = useQuickSearchResults(query, ref('station'))
            return () => null
        },
    }))
    return {query, results}
}

async function typed(query: {value: string}, text: string) {
    query.value = text
    await vi.advanceTimersByTimeAsync(300)
}

function attendanceSection(results: ReturnType<typeof useQuickSearchResults>) {
    return results.sections.value.find(section => section.key === 'attendance')
}

/**
 * Past attendance sheets are found from the palette by what was typed, never listed unasked, and
 * only for somebody who may read them on a station that keeps them.
 */
describe('the attendance section of the quick search', () => {
    beforeEach(() => {
        vi.useFakeTimers()
        readsAttendance = true
        attendanceOn = true
        listSessionSummaries.mockReset()
        listSessionSummaries.mockResolvedValue([SHEET])
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it('names the sheets that match what was typed and opens one', async () => {
        const {query, results} = await palette()

        await typed(query, ' atemschutz ')

        expect(listSessionSummaries).toHaveBeenCalledWith({search: 'atemschutz', limit: 5})
        const item = attendanceSection(results)!.items[0]!
        expect(item.label).toBe('Dienstabend Atemschutz')
        expect(item.sublabel).toBe('10.11.2026')
        expect(item.to).toEqual({name: 'attendance-session', params: {id: 41}})
    })

    it('lists nothing before anything is typed', async () => {
        const {query, results} = await palette()

        await typed(query, '   ')

        expect(listSessionSummaries).not.toHaveBeenCalled()
        expect(attendanceSection(results)).toBeUndefined()
    })

    it('stays out of reach without the permission or the module', async () => {
        readsAttendance = false
        const withoutPermission = await palette()
        await typed(withoutPermission.query, 'atemschutz')
        expect(attendanceSection(withoutPermission.results)).toBeUndefined()

        readsAttendance = true
        attendanceOn = false
        const withoutModule = await palette()
        await typed(withoutModule.query, 'atemschutz')
        expect(attendanceSection(withoutModule.results)).toBeUndefined()

        expect(listSessionSummaries).not.toHaveBeenCalled()
    })
})
