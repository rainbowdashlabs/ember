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
const collapsed = ref(false)

vi.mock('@/composables/useSidebarCollapse', () => ({useSidebarCollapse: () => ({collapsed})}))

vi.mock('@/composables/useBreakpoint', () => ({useBreakpoint: () => ({isDesktop: ref(true)})}))

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

/** The text of what the open menu holds, which stands in the page rather than in the sidebar. */
function inMenu(selector: string): string {
    return document.querySelector(selector)?.textContent ?? ''
}

/**
 * The foot of the sidebar shows the place the reader is in and switches between their places: stations,
 * associations, their own account and the administration.
 */
describe('PlaceSwitcher', () => {
    beforeEach(() => {
        stationList.value = [{memberId: 1, stationId: 'nord', stationName: 'Nord'}]
        clusterList.value = []
        currentClusterId.value = null
        admin.value = false
        collapsed.value = false
        refreshClusterUnread.mockClear()
    })

    it('shows nothing to somebody whose own account is the only place there is', () => {
        stationList.value = []
        const switcher = mount(PlaceSwitcher)

        expect(switcher.find('[data-testid="place-switcher"]').exists()).toBe(false)
    })

    it('shows the place the reader is in and opens a list of every place with what waits there', async () => {
        admin.value = true
        const switcher = mount(PlaceSwitcher, {props: {name: 'Nord'}, attachTo: document.body})

        expect(switcher.find('[data-testid="place-switcher"]').text()).toContain('Nord')
        await switcher.find('[data-testid="place-switcher"]').trigger('click')
        await flushPromises()

        expect(inMenu('[data-place="station:nord"]')).toContain('3')
        expect(inMenu('[data-place="account"]')).toContain('Mein Konto')
        expect(inMenu('[data-place="admin"]')).toContain('Administration')
        expect(inMenu('[data-place="admin"]')).toContain('4')
        switcher.unmount()
    })

    it('stands the menu above the button, wide enough for long names, and beside the button in the rail', async () => {
        const button = {left: 12, right: 52, top: 700, bottom: 740}
        const switcher = mount(PlaceSwitcher, {props: {name: 'Nord'}, attachTo: document.body})
        switcher.element.getBoundingClientRect = () => DOMRect.fromRect({x: button.left, y: button.top, width: 40, height: 40})

        await switcher.find('[data-testid="place-switcher"]').trigger('click')
        const menu = document.querySelector<HTMLElement>('[data-testid="place-menu"]')!
        expect(menu.style.left).toBe('12px')
        expect(menu.style.bottom).toBe(`${window.innerHeight - button.top + 8}px`)
        expect(menu.className).toContain('w-max')
        expect(menu.className).not.toContain('truncate')

        await switcher.find('[data-testid="place-switcher"]').trigger('click')
        collapsed.value = true
        await flushPromises()
        expect(switcher.find('[data-testid="place-switcher"]').text()).toBe('')
        await switcher.find('[data-testid="place-switcher"]').trigger('click')
        expect(document.querySelector<HTMLElement>('[data-testid="place-menu"]')!.style.left).toBe(`${button.right + 8}px`)
        switcher.unmount()
    })

    it('offers all stations at once to somebody with several', async () => {
        stationList.value = [...stationList.value, {memberId: 2, stationId: 'sued', stationName: 'Süd'}]
        const switcher = mount(PlaceSwitcher, {props: {name: 'Nord'}, attachTo: document.body})

        await switcher.find('[data-testid="place-switcher"]').trigger('click')

        expect(inMenu('[data-testid="place-menu"]')).toContain('Alle Wachen')
        switcher.unmount()
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
