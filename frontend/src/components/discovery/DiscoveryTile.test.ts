/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import DiscoveryTile from './DiscoveryTile.vue'
import type {DiscoveryEntry} from '@/api/generated/schema'
import type {DiscoveryViewer} from '@/composables/useDiscoveryViewer'
import {createDiscoveryEntry, createRemoteDiscoveryEntry, federationManager} from '@/test/mocks/discovery'

/**
 * One tile on the discovery page, for a station of this instance and for one of another instance.
 *
 * @vitest-environment happy-dom
 *
 * <p>Both are drawn by the same rules: the same chips, links and buttons for the same flags, and only the
 * host line under the name tells them apart.
 */
describe('DiscoveryTile', () => {
    const offers: Partial<DiscoveryEntry> = {
        description: 'Wir sind da',
        addressLine: 'Hauptstraße 1',
        city: 'Musterstadt',
        hasPublicWiki: true,
        hasPublicCalendar: true,
        hasPublicBlog: true,
        waitingListOpen: true,
    }
    const local = createDiscoveryEntry({...offers, publicPageUrl: '/public/station/wache-hier'})
    const remote = createRemoteDiscoveryEntry({...offers, name: 'Wache Hier'})

    function tile(station: DiscoveryEntry, viewer: DiscoveryViewer = federationManager()) {
        return mount(DiscoveryTile, {props: {station, viewer}})
    }

    function visibleText(station: DiscoveryEntry): string {
        const wrapper = tile(station, {...federationManager(), offersInvite: false})
        wrapper.findAll('.sr-only').forEach(hidden => hidden.element.remove())
        return wrapper.text().replace(/\s+/g, ' ')
    }

    function hrefs(station: DiscoveryEntry, base: string): string[] {
        return tile(station).findAll('a').map(a => (a.attributes('href') ?? '').replace(base, ''))
    }

    it('draws a local and a remote station with the same flags alike, apart from the host', () => {
        expect(visibleText(remote).replace('feuer.example', '')).toBe(visibleText(local))
        expect(visibleText(remote)).toContain('feuer.example')
        expect(visibleText(local)).not.toContain('feuer.example')
    })

    it('links the same parts of the public page for both, each on the instance the station lives on', () => {
        const parts = ['/waitlist', '/knowledge', '/calendar', '/blog', '']

        expect(hrefs(local, '/public/station/wache-hier')).toEqual(parts)
        expect(hrefs(remote, 'https://feuer.example:8443/public/station/wache-dort')).toEqual(parts)
    })

    it('shows the host address alone, with no words in front of it on screen', () => {
        const host = tile(remote).find('span.truncate')

        expect(host.text()).toBe('Instanz: feuer.example')
        expect(host.find('.sr-only').text()).toBe('Instanz:')
    })

    it('names each offer as a chip and the address the station published', () => {
        const text = visibleText(local)

        for (const label of ['Warteliste offen', 'Wiki', 'Kalender', 'Blog', 'Zur Wache', 'Hauptstraße 1, Musterstadt']) {
            expect(text).toContain(label)
        }
    })

    it('marks the links to another instance as leaving the site and keeps the local ones in the app', () => {
        const away = tile(remote).findAll('a')
        const home = tile(local).findAll('a')

        expect(away.every(a => a.attributes('rel') === 'external noopener noreferrer')).toBe(true)
        expect(home.every(a => a.attributes('rel') === undefined)).toBe(true)
    })

    it('shows no chips and no link to a station that publishes nothing', () => {
        const bare = tile(createDiscoveryEntry())

        expect(bare.findAll('a')).toHaveLength(0)
        expect(bare.find('ul').exists()).toBe(false)
    })

    it('names the station to screen readers on every chip and on its page link', () => {
        const readers = tile(remote).findAll('.sr-only').map(s => s.text())

        expect(readers).toContain('bei Wache Hier')
        expect(readers).toContain('Wache Hier auf feuer.example')
    })

    describe('the request to federate', () => {
        const anonymous: DiscoveryViewer = {mayRequestFederation: false, stationUid: null, offersInvite: true}
        const memberWithout: DiscoveryViewer = {mayRequestFederation: false, stationUid: 'x', offersInvite: true}

        function offersConnect(station: DiscoveryEntry, viewer: DiscoveryViewer): boolean {
            return tile(station, viewer).text().includes('Verbinden')
        }

        it('is not offered to an anonymous visitor', () => {
            expect(offersConnect(local, anonymous)).toBe(false)
            expect(offersConnect(remote, anonymous)).toBe(false)
        })

        it('is not offered to a member without the federation permission', () => {
            expect(offersConnect(local, memberWithout)).toBe(false)
            expect(offersConnect(remote, memberWithout)).toBe(false)
        })

        it('is offered to a federation manager on local and remote tiles alike', () => {
            expect(offersConnect(local, federationManager('other'))).toBe(true)
            expect(offersConnect(remote, federationManager('other'))).toBe(true)
        })

        it('is never offered on the viewer\'s own station, even where the list does not say it is theirs', () => {
            const own = tile(local, federationManager(local.stationUid))

            expect(own.text()).not.toContain('Verbinden')
            expect(own.text()).toContain('Meine Wache')
            expect(offersConnect({...local, isOwnStation: true}, federationManager())).toBe(false)
        })

        it('is not offered where a partnership exists or the station takes no requests', () => {
            expect(offersConnect({...remote, alreadyFederated: true}, federationManager())).toBe(false)
            expect(offersConnect({...remote, acceptsFederation: false}, federationManager())).toBe(false)
        })

        it('treats a remote station under the viewer\'s own identifier as somebody else', () => {
            expect(offersConnect(remote, federationManager(remote.stationUid))).toBe(true)
        })
    })

    it('hands out an invite code for a station of every instance alike', () => {
        expect(tile(local, federationManager()).text()).toContain('Code anfordern')
        expect(tile(remote, federationManager()).text()).toContain('Code anfordern')
        expect(tile({...remote, acceptsFederation: false}, federationManager()).text()).not.toContain('Code anfordern')
        expect(tile(local, {...federationManager(), offersInvite: false}).text()).not.toContain('Code anfordern')
    })

    it('asks to federate with the station it shows', async () => {
        const wrapper = tile(remote, federationManager())

        await wrapper.findAll('button').find(b => b.text().includes('Verbinden'))!.trigger('click')

        expect(wrapper.emitted('connect')).toEqual([[remote]])
    })
})
