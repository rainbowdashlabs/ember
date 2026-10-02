/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import DiscoveryGrid from './DiscoveryGrid.vue'
import type {DiscoveryEntry} from '@/api/generated/schema'
import {createDiscoveryEntry, createRemoteDiscoveryEntry, federationManager} from '@/test/mocks/discovery'

/**
 * The tiles on the discovery page, for stations of this instance and of other instances.
 *
 * @vitest-environment happy-dom
 */
describe('DiscoveryGrid', () => {
    const remote = createRemoteDiscoveryEntry()

    function grid(stations: DiscoveryEntry[]) {
        return mount(DiscoveryGrid, {props: {stations, viewer: federationManager()}})
    }

    it('shows the logo of a remote station from the copy this instance keeps of it', () => {
        const cached = '/api/v1/public/discovery/remote/ab12/00000000-0000-0000-0000-000000000002/logo?size=128'
        const logo = grid([{...remote, hasLogo: true, logoUrl: cached}]).find('img')

        expect(logo.attributes('src')).toBe(cached)
    })

    it('shows the logo of a station of this instance from the address it is given', () => {
        const own = '/api/v1/public/stations/00000000-0000-0000-0000-000000000001/logo?size=128'
        const logo = grid([createDiscoveryEntry({hasLogo: true, logoUrl: own})]).find('img')

        expect(logo.attributes('src')).toBe(own)
    })

    it('shows the placeholder where no logo can be shown', () => {
        expect(grid([remote]).find('img').exists()).toBe(false)
    })

    it('shows a local and a remote station under the same identifier side by side', () => {
        const card = grid([createDiscoveryEntry(), {...remote, stationUid: '00000000-0000-0000-0000-000000000001'}])

        expect(card.text()).toContain('Wache Hier')
        expect(card.text()).toContain('Wache Dort')
    })

    it('passes a request to federate on with the station it is for', async () => {
        const wrapper = grid([remote])

        await wrapper.find('button').trigger('click')

        expect(wrapper.emitted('connect')).toEqual([[remote]])
    })
})
