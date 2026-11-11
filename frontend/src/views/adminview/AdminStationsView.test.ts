/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {ReminderResult, ReminderTrigger, StationState, type AdminStationSummary} from '@/api/generated/schema'
import AdminStationsView from './AdminStationsView.vue'

const listAdminStations = vi.hoisted(() => vi.fn())
const remindManager = vi.hoisted(() => vi.fn())
const replace = vi.hoisted(() => vi.fn())
const push = vi.hoisted(() => vi.fn())
const route = vi.hoisted(() => ({params: {} as Record<string, string>, query: {} as Record<string, string>}))

vi.mock('@/api/stations', () => ({
    listAdminStations,
    remindManager,
    deleteStation: vi.fn(),
}))

vi.mock('vue-router', async (importOriginal) => ({
    ...(await importOriginal<typeof import('vue-router')>()),
    useRoute: () => route,
    useRouter: () => ({push, replace}),
}))

function station(id: string, name: string, state: StationState): AdminStationSummary {
    return {
        id,
        name,
        state,
        memberCount: 4,
        managerName: 'Mara Nager',
        managerEmail: 'mara@test.example',
        waitingSince: '2026-11-01T10:00:00Z',
        createdAt: '2026-10-01T10:00:00Z',
        associationName: null,
        lastReminder: null,
        movedAway: false,
    }
}

const STATIONS = [
    station('00000000-0000-0000-0000-000000000001', 'Nord', StationState.SET_UP),
    station('00000000-0000-0000-0000-000000000002', 'Süd', StationState.WAITING_FOR_MANAGER),
    station('00000000-0000-0000-0000-000000000003', 'West', StationState.SETTING_UP),
    station('00000000-0000-0000-0000-000000000004', 'Ost', StationState.NO_MANAGER),
]

/**
 * The station list of the instance administration: filtered by state from the address, drawn as tiles
 * or as a table, with the reminder to a waiting station's manager.
 */
describe('AdminStationsView', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        route.query = {}
        listAdminStations.mockResolvedValue(STATIONS)
    })

    async function page() {
        const view = await mountSuspended(AdminStationsView)
        await flushPromises()
        return view
    }

    it('shows every station as a tile with its state', async () => {
        const view = await page()

        const tiles = view.findAll('[data-testid="station-tile"]')
        expect(tiles).toHaveLength(4)
        expect(tiles[0]!.text()).toContain('Eingerichtet')
        expect(tiles[1]!.text()).toContain('Wartet auf Verwaltung')
        expect(tiles[3]!.text()).toContain('Ohne Verwaltung')
        expect(view.findAll('[data-testid="remind-manager"]')).toHaveLength(2)
    })

    it('shows only the waiting stations the overview links to with ?state=waiting', async () => {
        route.query = {state: 'waiting'}
        const view = await page()

        const names = view.findAll('[data-testid="station-tile"]').map(tile => tile.text())
        expect(names).toHaveLength(2)
        expect(names[0]).toContain('Süd')
        expect(names[1]).toContain('West')
    })

    it('says so where a filter matches no station', async () => {
        route.query = {state: 'no-manager'}
        listAdminStations.mockResolvedValue(STATIONS.slice(0, 3))
        const view = await page()

        expect(view.findAll('[data-testid="station-tile"]')).toHaveLength(0)
        expect(view.find('[data-testid="stations-empty-filter"]').text()).toBe('Keine Wache passt zu diesem Filter.')
    })

    it('says nothing of the sort while every station is shown', async () => {
        listAdminStations.mockResolvedValue([])
        const view = await page()

        expect(view.find('[data-testid="stations-empty-filter"]').exists()).toBe(false)
    })

    it('marks the chosen filter and view for assistive technology', async () => {
        route.query = {state: 'waiting'}
        const view = await page()

        expect(view.find('[data-testid="station-filter-waiting"]').attributes('role')).toBe('radio')
        expect(view.find('[data-testid="station-filter-waiting"]').attributes('aria-checked')).toBe('true')
        expect(view.find('[data-testid="station-filter-all"]').attributes('aria-checked')).toBe('false')
        expect(view.find('[data-testid="station-view-tiles"]').attributes('aria-checked')).toBe('true')
    })

    it('puts the chosen filter into the address and takes it out again for every station', async () => {
        route.query = {state: 'waiting'}
        const view = await page()

        await view.find('[data-testid="station-filter-no-manager"]').trigger('click')
        expect(replace).toHaveBeenLastCalledWith({query: {state: 'no-manager'}})
        await view.find('[data-testid="station-filter-all"]').trigger('click')
        expect(replace).toHaveBeenLastCalledWith({query: {}})
    })

    it('switches to the table with the same stations', async () => {
        const view = await page()

        await view.find('[data-testid="station-view-table"]').trigger('click')
        await flushPromises()

        expect(view.find('[data-testid="stations-table"]').exists()).toBe(true)
        expect(view.findAll('[data-testid="station-tile"]')).toHaveLength(0)
        expect(view.text()).toContain('Süd')
        expect(view.findAll('[data-testid="last-reminder"]')).toHaveLength(0)
    })

    it('shows the last reminder once a reminder went out', async () => {
        remindManager.mockResolvedValue({
            station: {...STATIONS[1]!, lastReminder: {sentAt: '2026-11-11T09:00:00Z', trigger: ReminderTrigger.MANUAL}},
            result: ReminderResult.SENT,
        })
        const view = await page()

        await view.findAll('[data-testid="remind-manager"]')[0]!.trigger('click')
        await flushPromises()

        expect(remindManager).toHaveBeenCalledWith(STATIONS[1]!.id)
        expect(view.find('[data-testid="last-reminder"]').text()).toBe('Zuletzt erinnert am 11.11.2026')
    })
})
