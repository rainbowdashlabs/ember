/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {defineComponent, h} from 'vue'
import {flushPromises, mount} from '@vue/test-utils'
import {createMemoryHistory, createRouter} from 'vue-router'
import DiscoveryExplorer from './DiscoveryExplorer.vue'
import DiscoveryTile from './DiscoveryTile.vue'
import {SEARCH_DELAY_MS} from './discoverySearch'
import type {DiscoveryEntry} from '@/api/generated/schema'
import {createDiscoveryEntry, createRemoteDiscoveryEntry, invitingPage} from '@/test/mocks/discovery'

/**
 * A discovery page: the map on top with the chosen station's tile below it, the search between the map and
 * the list, and the chosen station kept in the address.
 *
 * @vitest-environment happy-dom
 *
 * <p>The map itself is a stand-in that shows the pins it is handed and reports a click on one, which is
 * all the page asks of it.
 */
describe('DiscoveryExplorer', () => {
    const MapStub = defineComponent({
        name: 'StationMap',
        props: {stations: {type: Array, required: true}, selectedUid: {type: String, default: null}},
        emits: ['marker-click', 'ready'],
        setup(props, {expose}) {
            expose({center: () => undefined})
            return () => h('div', {'data-testid': 'map', 'data-selected': props.selectedUid ?? ''},
                (props.stations as {uid: string; name: string}[]).map(pin => h('span', {'data-pin': pin.uid}, pin.name)))
        },
    })

    const nord = createDiscoveryEntry({
        stationUid: '00000000-0000-0000-0000-00000000000a',
        name: 'Wache Nord',
        latitude: 53.5,
        longitude: 10.0,
    })
    const sued = createRemoteDiscoveryEntry({
        stationUid: '00000000-0000-0000-0000-00000000000b',
        name: 'Wache Süd',
        latitude: 48.1,
        longitude: 11.6,
    })
    const ohneOrt = createDiscoveryEntry({stationUid: '00000000-0000-0000-0000-00000000000c', name: 'Wache Ohne Ort'})
    const stations: DiscoveryEntry[] = [nord, sued, ohneOrt]

    beforeEach(() => {
        vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout']})
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    async function explorer(address = '/discovery', search = '') {
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{path: '/discovery', component: {template: '<div/>'}}],
        })
        await router.push(address)
        const wrapper = mount(DiscoveryExplorer, {
            props: {stations, viewer: invitingPage(), selectionKey: 'station', search},
            global: {plugins: [router], stubs: {StationMap: MapStub}},
        })
        await flushPromises()
        return {wrapper, router}
    }

    type Explorer = Awaited<ReturnType<typeof explorer>>['wrapper']

    function pins(wrapper: Explorer): string[] {
        return wrapper.findAll('[data-pin]').map(pin => pin.text())
    }

    function chosenTile(wrapper: Explorer): DiscoveryEntry[] {
        return wrapper.findAllComponents(DiscoveryTile)
            .filter(tile => tile.props('large'))
            .map(tile => tile.props('station'))
    }

    it('puts the map first, then the search, then the list, with no switch between list and map', async () => {
        const {wrapper} = await explorer()
        const html = wrapper.html()

        expect(html.indexOf('data-testid="map"')).toBeLessThan(html.indexOf('<input'))
        expect(html.indexOf('<input')).toBeLessThan(html.indexOf('Wache Ohne Ort'))
        expect(wrapper.text()).not.toContain('Liste')
        expect(wrapper.text()).not.toMatch(/\bKarte\b/)
    })

    it('pins only the stations that have a place, local and remote alike', async () => {
        const {wrapper} = await explorer()

        expect(pins(wrapper)).toEqual(['Wache Nord', 'Wache Süd'])
        expect(wrapper.text()).toContain('Wache Ohne Ort')
    })

    it('opens the tile of a clicked pin right below the map and keeps it in the address', async () => {
        const {wrapper, router} = await explorer()

        wrapper.findComponent(MapStub).vm.$emit('marker-click', 'feuer.example/' + sued.stationUid)
        await flushPromises()

        expect(router.currentRoute.value.query.station).toBe(sued.stationUid)
        expect(chosenTile(wrapper)).toEqual([sued])
        expect(wrapper.find('section [aria-label="Schließen"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="map"]').attributes('data-selected')).toBe('feuer.example/' + sued.stationUid)
    })

    it('opens the station a link names', async () => {
        const {wrapper} = await explorer(`/discovery?station=${nord.stationUid}`)

        expect(chosenTile(wrapper)).toEqual([nord])
        expect(wrapper.find('[data-testid="map"]').attributes('data-selected')).toBe('/' + nord.stationUid)
    })

    it('closes the chosen tile and forgets it in the address', async () => {
        const {wrapper, router} = await explorer(`/discovery?station=${nord.stationUid}`)

        await wrapper.find('section [aria-label="Schließen"]').trigger('click')
        await flushPromises()

        expect(router.currentRoute.value.query.station).toBeUndefined()
        expect(chosenTile(wrapper)).toEqual([])
    })

    it('chooses a station from the list and brings the map back into view', async () => {
        const {wrapper, router} = await explorer()
        let scrolled = false
        wrapper.find('section').element.parentElement!.scrollIntoView = () => {
            scrolled = true
        }

        const locate = wrapper.findAll('button').find(button => button.text().includes('Auf der Karte'))!
        await locate.trigger('click')
        await flushPromises()

        expect(router.currentRoute.value.query.station).toBe(nord.stationUid)
        expect(scrolled).toBe(true)
    })

    it('offers to show on the map only a station that has a place there', async () => {
        const {wrapper} = await explorer()

        expect(wrapper.findAll('button').filter(button => button.text().includes('Auf der Karte'))).toHaveLength(2)
    })

    it('filters the map and the list together and closes a chosen station the search no longer finds', async () => {
        const {wrapper, router} = await explorer(`/discovery?station=${nord.stationUid}`)

        await wrapper.setProps({search: 'Süd'})
        await vi.advanceTimersByTimeAsync(SEARCH_DELAY_MS)
        await flushPromises()

        expect(pins(wrapper)).toEqual(['Wache Süd'])
        expect(wrapper.text()).not.toContain('Wache Ohne Ort')
        expect(router.currentRoute.value.query.station).toBeUndefined()
    })

    it('waits for typing to pause before it filters', async () => {
        const {wrapper} = await explorer()

        await wrapper.setProps({search: 'Sü'})
        await vi.advanceTimersByTimeAsync(SEARCH_DELAY_MS - 50)
        await wrapper.setProps({search: 'Süd'})
        await vi.advanceTimersByTimeAsync(SEARCH_DELAY_MS - 50)
        expect(pins(wrapper)).toEqual(['Wache Nord', 'Wache Süd'])

        await vi.advanceTimersByTimeAsync(50)
        expect(pins(wrapper)).toEqual(['Wache Süd'])
    })

    it('filters by the term a link hands over right away', async () => {
        const {wrapper} = await explorer('/discovery', 'Süd')

        expect(pins(wrapper)).toEqual(['Wache Süd'])
    })
})
