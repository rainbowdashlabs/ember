/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it} from 'vitest'
import {createMemoryHistory, createRouter, type Router} from 'vue-router'
import {isInArea, namePlaceInLinks, placeQuery, withoutPlace} from './placeInAddress'

const page = {template: '<div/>'}

/** A router with a page in each area and one outside them, its links naming the given place. */
function routerAt(place: {station: string | null, cluster: string | null}): Router {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            {path: '/station/events/:id', name: 'event', component: page},
            {path: '/station/dashboard/overview', component: page},
            {path: '/cluster/stations', component: page},
            {path: '/account', component: page},
        ],
    })
    namePlaceInLinks(router, () => place.station, () => place.cluster)
    return router
}

describe('the area of an address', () => {
    it('holds the area itself and its pages, nothing that only starts alike', () => {
        expect(isInArea('/station', '/station')).toBe(true)
        expect(isInArea('/station/events', '/station')).toBe(true)
        expect(isInArea('/station-select', '/station')).toBe(false)
        expect(isInArea('/cluster/stations', '/station')).toBe(false)
    })
})

describe('the place an address names', () => {
    it('adds the station to a station page and keeps the rest of the query', () => {
        expect(placeQuery('/station/events', {view: 'list'}, 's1', 'c1')).toEqual({view: 'list', station: 's1'})
    })

    it('adds the cluster to a cluster page', () => {
        expect(placeQuery('/cluster/stations', {}, 's1', 'c1')).toEqual({cluster: 'c1'})
    })

    it('leaves an address alone that already names a place, a page outside both areas, or a tab without one', () => {
        expect(placeQuery('/station/events', {station: 'other'}, 's1', null)).toBeNull()
        expect(placeQuery('/account', {}, 's1', 'c1')).toBeNull()
        expect(placeQuery('/station/events', {}, null, 'c1')).toBeNull()
    })
})

describe('links of a tab', () => {
    let place: {station: string | null, cluster: string | null}

    beforeEach(() => {
        place = {station: 's1', cluster: 'c1'}
    })

    it('name the station of the tab, so a link opened in a new tab opens the same station', () => {
        const router = routerAt(place)

        expect(router.resolve('/station/dashboard/overview').href).toBe('/station/dashboard/overview?station=s1')
        expect(router.resolve({name: 'event', params: {id: '7'}, hash: '#signups'}).href)
            .toBe('/station/events/7?station=s1#signups')
    })

    it('name the cluster of the tab on cluster pages and nothing on other pages', () => {
        const router = routerAt(place)

        expect(router.resolve('/cluster/stations').href).toBe('/cluster/stations?cluster=c1')
        expect(router.resolve('/account').href).toBe('/account')
    })

    it('keep a station the link names on purpose', () => {
        const router = routerAt(place)

        expect(router.resolve('/station/dashboard/overview?station=s2').href)
            .toBe('/station/dashboard/overview?station=s2')
    })

    it('follow the place of the tab as it becomes known', () => {
        place.station = null
        const router = routerAt(place)
        expect(router.resolve('/station/dashboard/overview').href).toBe('/station/dashboard/overview')

        place.station = 's3'

        expect(router.resolve('/station/dashboard/overview').href).toBe('/station/dashboard/overview?station=s3')
    })
})

describe('an address a picker returns to', () => {
    /** Picking another station must not land back in the one the address was taken in. */
    it('names no station or cluster and keeps everything else', () => {
        expect(withoutPlace('/station/events/7?station=s1&tab=signups#top')).toBe('/station/events/7?tab=signups#top')
        expect(withoutPlace('/cluster/stations?cluster=c1')).toBe('/cluster/stations')
        expect(withoutPlace('/station/dashboard/overview')).toBe('/station/dashboard/overview')
    })
})
