/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, beforeEach, vi} from 'vitest'
import {mockNuxtImport} from '@nuxt/test-utils/runtime'
import type {RouteLocationNormalized} from 'vue-router'
import middleware from './auth.global'

/**
 * What the route guard does with an idle session, and in which order.
 *
 * The order is the whole point: the active station is resolved before anything else may redirect,
 * because a redirect that fires first drops the {@code ?station=} parameter it was carrying and
 * sends the visitor to the station picker instead of where they were going.
 */
const state = vi.hoisted(() => ({
    store: new Map<string, string>(),
    activeStation: null as string | null,
    navigations: [] as unknown[],
    admin: false,
    sessionLoaded: false,
    sessionLoads: 0,
    clusters: [] as {uid: string}[],
    clustersLoaded: false,
}))

mockNuxtImport('navigateTo', () => (target: unknown) => {
    state.navigations.push(target)
    return target
})

vi.mock('~/api/storage', () => ({
    getItem: (key: string) => state.store.get(key) ?? null,
}))

vi.mock('~/composables/useConsentGuard', () => ({
    useConsentGuard: () => ({needsReconsent: {value: false}}),
}))

vi.mock('~/composables/useSession', () => ({
    useSession: () => ({
        loaded: {value: state.sessionLoaded},
        load: async () => {
            state.sessionLoads++
            state.sessionLoaded = true
        },
        isAdmin: () => state.admin,
    }),
}))

vi.mock('~/composables/useCluster', () => ({
    useCluster: () => ({
        loaded: {value: state.clustersLoaded},
        load: async () => {
            state.clustersLoaded = true
        },
        hasClusters: {value: state.clusters.length > 0},
    }),
}))

vi.mock('~/composables/useStations', () => ({
    useStations: () => ({
        setActiveStation: (stationId: string) => {
            state.activeStation = stationId
            state.store.set('station_id', stationId)
        },
    }),
}))

const HOUR = 3600000
const STATION = '11111111-1111-4111-a111-111111111111'

function route(path: string, query: Record<string, string> = {}): RouteLocationNormalized {
    const search = new URLSearchParams(query).toString()
    return {
        path,
        fullPath: search ? `${path}?${search}` : path,
        query,
        meta: {},
    } as unknown as RouteLocationNormalized
}

function run(to: RouteLocationNormalized) {
    return (middleware as unknown as (to: RouteLocationNormalized) => Promise<unknown>)(to)
}

/**
 * Puts the session past the idle limit without touching the clock: the stamp is simply old.
 */
function idleForAnHour() {
    localStorage.setItem('ember_last_activity', String(Date.now() - HOUR - 1000))
}

describe('auth route guard', () => {
    beforeEach(() => {
        state.store.clear()
        state.store.set('session_token', 'token')
        state.activeStation = null
        state.navigations = []
        state.admin = false
        state.sessionLoaded = false
        state.sessionLoads = 0
        state.clusters = []
        state.clustersLoaded = false
        localStorage.clear()
    })

    /**
     * The reported bug: an hour of idleness plus a link carrying its own station used to end at the
     * station picker, because the idle redirect ran first and left the parameter behind.
     */
    it('takes the station from the link before sending an idle session to the requirements', async () => {
        idleForAnHour()

        await run(route('/station/dashboard/overview', {station: STATION}))

        expect(state.activeStation, 'the link handed its station over').toBe(STATION)
        expect(state.navigations).toEqual([
            {path: '/station/requirements', query: {redirect: `/station/dashboard/overview?station=${STATION}`}},
        ])
    })

    /**
     * Without a station there is nothing to hold requirements, so the picker comes first - and the
     * idle window must survive it, or the requirements are silently skipped for the session.
     */
    it('sends a session without a station to the picker and keeps the idle window armed', async () => {
        idleForAnHour()
        const stamp = localStorage.getItem('ember_last_activity')

        await run(route('/station/dashboard/overview'))

        expect(state.navigations).toEqual([
            {path: '/cross-station', query: {redirect: '/station/dashboard/overview'}},
        ])
        expect(localStorage.getItem('ember_last_activity'), 'the stamp was not refreshed').toBe(stamp)
    })

    /**
     * The requirements page is itself under {@code /station/}, so it has to be reachable rather
     * than bounced onwards once a station is known.
     */
    it('lets the requirements page through', async () => {
        state.store.set('station_id', STATION)
        idleForAnHour()

        await run(route('/station/requirements'))

        expect(state.navigations).toEqual([])
    })

    /**
     * A session that is not idle is stamped and left alone.
     */
    it('stamps a live session and redirects nothing', async () => {
        state.store.set('station_id', STATION)
        localStorage.setItem('ember_last_activity', String(Date.now() - 1000))

        await run(route('/station/dashboard/overview'))

        expect(state.navigations).toEqual([])
        expect(Number(localStorage.getItem('ember_last_activity'))).toBeGreaterThan(Date.now() - 1000)
    })

    /**
     * The administration area opens for an administrator and for nobody else. The server refuses
     * every call underneath it anyway; what this prevents is a panel that opens and then fails on
     * each of them.
     */
    it('turns a member away from the administration area', async () => {
        state.store.set('station_id', STATION)
        state.admin = false

        await run(route('/admin/settings/legal'))

        expect(state.navigations).toEqual(['/station/dashboard/overview'])
    })

    it('lets an administrator in', async () => {
        state.store.set('station_id', STATION)
        state.admin = true

        await run(route('/admin/settings/legal'))

        expect(state.navigations).toEqual([])
    })

    /**
     * The decision needs the session, so it is fetched once when the guard runs before anything
     * else has loaded it.
     */
    it('loads the session before deciding, and only when it has to', async () => {
        state.store.set('station_id', STATION)
        state.admin = true

        await run(route('/admin/dashboard/overview'))
        await run(route('/admin/monitoring/traffic'))

        expect(state.sessionLoads, 'loaded once, then reused').toBe(1)
    })

    /**
     * The cluster area is for people who have one. Somebody who does not is sent back rather than
     * shown a shell explaining that they are working for nobody, which reads as a page they belong on.
     */
    it('turns an account with no cluster away from the cluster area', async () => {
        state.store.set('station_id', STATION)

        await run(route('/cluster/stations'))

        expect(state.navigations).toEqual(['/station/dashboard/overview'])
    })

    it('lets somebody who may act for a cluster in', async () => {
        state.store.set('station_id', STATION)
        state.clusters = [{uid: 'c1'}]

        await run(route('/cluster'))

        expect(state.navigations).toEqual([])
    })
})
