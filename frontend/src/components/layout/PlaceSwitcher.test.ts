/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {computed, ref} from 'vue'
import PlaceSwitcher from './PlaceSwitcher.vue'

const stationList = ref([{memberId: 1, stationId: 'nord', stationName: 'Nord'}])
const clusterList = ref<{uid: string, name: string}[]>([])
const currentClusterId = ref<string | null>(null)
const admin = ref(false)
const refreshClusterUnread = vi.fn(async () => {})

vi.mock('vue-router', () => ({useRoute: () => ({path: '/station/news'})}))

vi.mock('@/api/session', () => ({
    getCrossStationDashboard: vi.fn(async () => ({
        stations: [{stationId: 'nord', stationName: 'Nord', notifications: 2, requirements: 1}],
        notifications: [],
    })),
}))

vi.mock('@/composables/useStations', () => ({
    useStations: () => ({
        stationList,
        loaded: ref(true),
        load: vi.fn(),
        currentStationId: ref('nord'),
        setActiveStation: vi.fn(),
        getStationLogoUrl: () => null,
        loadAllLogos: vi.fn(async () => {}),
    }),
}))

vi.mock('@/composables/useCluster', () => ({
    useCluster: () => ({clusterList, loaded: ref(true), load: vi.fn(), currentClusterId, setActiveCluster: vi.fn()}),
}))

vi.mock('@/composables/useSession', () => ({useSession: () => ({isAdmin: () => admin.value})}))

vi.mock('@/composables/useMonitoringCounts', () => ({
    useMonitoringCounts: () => ({counts: computed(() => ({problemLog: 3, problemReports: 1})), refresh: vi.fn(async () => {})}),
}))

vi.mock('@/composables/useClusterNotificationCount', () => ({
    useClusterNotificationCount: () => ({count: ref(0), refresh: refreshClusterUnread}),
}))

/**
 * The name at the top of the sidebar switches places only when there is somewhere else to go.
 */
describe('PlaceSwitcher', () => {
    beforeEach(() => {
        stationList.value = [{memberId: 1, stationId: 'nord', stationName: 'Nord'}]
        clusterList.value = []
        currentClusterId.value = null
        admin.value = false
        refreshClusterUnread.mockClear()
    })

    it('stays plain text for somebody with one station and nothing else', () => {
        const switcher = mount(PlaceSwitcher, {props: {name: 'Nord'}})

        expect(switcher.text()).toBe('Nord')
        expect(switcher.find('[data-testid="place-switcher"]').exists()).toBe(false)
    })

    it('lists the stations and the administration, each with what waits there', async () => {
        admin.value = true
        const switcher = mount(PlaceSwitcher, {props: {name: 'Nord'}})

        await switcher.find('[data-testid="place-switcher"]').trigger('click')
        await flushPromises()

        expect(switcher.find('[data-place="station:nord"]').text()).toContain('3')
        expect(switcher.find('[data-place="admin"]').text()).toContain('Administration')
        expect(switcher.find('[data-place="admin"]').text()).toContain('4')
    })

    it('offers all stations at once to somebody with several', async () => {
        stationList.value = [...stationList.value, {memberId: 2, stationId: 'sued', stationName: 'Süd'}]
        const switcher = mount(PlaceSwitcher, {props: {name: 'Nord'}})

        await switcher.find('[data-testid="place-switcher"]').trigger('click')

        expect(switcher.text()).toContain('Alle Wachen')
    })

    /** The association's count is refused outside an association, which showed as a refusal on opening the menu. */
    it('counts the notices of an association only where one is chosen that the reader belongs to', async () => {
        admin.value = true
        const switcher = mount(PlaceSwitcher, {props: {name: 'Nord'}})
        await switcher.find('[data-testid="place-switcher"]').trigger('click')
        await flushPromises()
        expect(refreshClusterUnread).not.toHaveBeenCalled()

        clusterList.value = [{uid: 'verband', name: 'Verband'}]
        currentClusterId.value = 'verband'
        await switcher.find('[data-testid="place-switcher"]').trigger('click')
        await switcher.find('[data-testid="place-switcher"]').trigger('click')
        await flushPromises()
        expect(refreshClusterUnread).toHaveBeenCalledOnce()
    })
})
