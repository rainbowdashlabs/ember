/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended, registerEndpoint} from '@nuxt/test-utils/runtime'
import {defineEventHandler} from 'h3'
import PublicDiscoveryView from './PublicDiscoveryView.vue'
import {createDiscoveryEntry, createRemoteDiscoveryEntry} from '@/test/mocks/discovery'

/**
 * The public discovery page opens with the map, has no switch between a list and a map any more, and
 * opens the station a link names.
 */
describe('PublicDiscoveryView', () => {
    const nord = createDiscoveryEntry({name: 'Wache Nord', latitude: 53.5, longitude: 10.0})
    const sued = createRemoteDiscoveryEntry({name: 'Wache Süd', latitude: 48.1, longitude: 11.6})

    registerEndpoint('/api/v1/public/discovery', defineEventHandler(() => [nord, sued]))

    const MapStub = {name: 'StationMap', template: '<div data-testid="map"/>', methods: {center: () => undefined}}

    async function page(address: string) {
        return mountSuspended(PublicDiscoveryView, {route: address, global: {stubs: {StationMap: MapStub}}})
    }

    it('shows the map above the search and offers no list or map tab', async () => {
        const wrapper = await page('/discovery')
        const html = wrapper.html()

        expect(html.indexOf('data-testid="map"')).toBeGreaterThan(-1)
        expect(html.indexOf('data-testid="map"')).toBeLessThan(html.indexOf('<input'))
        expect(wrapper.findAll('button').map(button => button.text())).not.toContain('Liste')
        expect(wrapper.findAll('button').map(button => button.text())).not.toContain('Karte')
    })

    it('opens the tile of the station the address names', async () => {
        const wrapper = await page(`/discovery?station=${sued.stationUid}`)

        expect(wrapper.find('section [aria-label="Schließen"]').exists()).toBe(true)
        expect(wrapper.find('section').text()).toContain('feuer.example')
    })
})
