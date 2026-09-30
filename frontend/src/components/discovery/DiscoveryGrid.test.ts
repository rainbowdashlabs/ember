/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import DiscoveryGrid from './DiscoveryGrid.vue'
import type {DiscoveryEntry} from '@/api/discovery'

/**
 * A card on the discovery page for a station of this instance and for one of another instance.
 *
 * @vitest-environment happy-dom
 *
 * <p>The remote card says where the station lives, leads to its page on that instance and offers
 * nothing that only works between stations of this instance.
 */
describe('DiscoveryGrid', () => {
    const RouterLink = {props: ['to'], template: '<a data-testid="local-link"><slot/></a>'}

    function entry(overrides: Partial<DiscoveryEntry>): DiscoveryEntry {
        return {
            stationUid: '00000000-0000-0000-0000-000000000001',
            name: 'Wache Hier',
            description: null,
            hasLogo: false,
            hasPublicKb: true,
            hasPublicCalendar: false,
            alreadyFederated: false,
            isOwnStation: false,
            publicSlug: 'wache-hier',
            city: null,
            country: null,
            latitude: null,
            longitude: null,
            ...overrides,
        }
    }

    const remote = entry({
        stationUid: '00000000-0000-0000-0000-000000000002',
        name: 'Wache Dort',
        hasPublicKb: false,
        publicSlug: 'wache-dort',
        instanceHost: 'feuer.example',
        publicPageUrl: 'https://feuer.example/public/station/wache-dort',
    })

    function grid(stations: DiscoveryEntry[]) {
        return mount(DiscoveryGrid, {
            props: {stations, canConnect: true, showInvite: true},
            global: {stubs: {RouterLink, 'router-link': RouterLink}},
        })
    }

    it('names the instance a remote station lives on, in words a screen reader reads too', () => {
        const card = grid([remote])

        expect(card.text()).toContain('Auf der Instanz feuer.example')
        expect(card.find('[data-testid="icon"]').exists()).toBe(true)
    })

    it('links a remote station to its public page on its own instance, in the same tab', () => {
        const link = grid([remote]).find('a[href^="https://"]')

        expect(link.attributes('href')).toBe('https://feuer.example/public/station/wache-dort')
        expect(link.attributes('target')).toBeUndefined()
        expect(link.attributes('rel')).toBe('external noopener noreferrer')
        expect(link.text()).toContain('Zur Wache')
        expect(link.find('.sr-only').text()).toBe('Wache Dort auf feuer.example')
    })

    it('offers a remote station neither a federation request nor an invite code', () => {
        const card = grid([remote])

        expect(card.text()).not.toContain('Verbinden')
        expect(card.text()).not.toContain('Code anfordern')
        expect(card.find('[data-testid="local-link"]').exists()).toBe(false)
    })

    it('keeps the federation actions and the local page for a station of this instance', () => {
        const card = grid([entry({})])

        expect(card.text()).toContain('Verbinden')
        expect(card.text()).toContain('Code anfordern')
        expect(card.find('[data-testid="local-link"]').exists()).toBe(true)
        expect(card.text()).not.toContain('Auf der Instanz')
    })

    it('shows a local and a remote station under the same identifier side by side', () => {
        const card = grid([entry({}), {...remote, stationUid: '00000000-0000-0000-0000-000000000001'}])

        expect(card.text()).toContain('Wache Hier')
        expect(card.text()).toContain('Wache Dort')
    })
})
