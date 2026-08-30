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
    needsReconsent: false,
    demo: {demo: false, dev: false},
    demoLogins: [] as string[],
    demoLoginFails: false,
    sessionCleared: 0,
}))

mockNuxtImport('navigateTo', () => (target: unknown) => {
    state.navigations.push(target)
    return target
})

vi.mock('~/api/storage', () => ({
    getItem: (key: string) => state.store.get(key) ?? null,
    removeItem: (key: string) => {
        state.store.delete(key)
    },
}))

vi.mock('~/api/demo', () => ({
    getDemoStatus: async () => state.demo,
}))

vi.mock('~/api/auth', () => ({
    demoLogin: async (email: string) => {
        if (state.demoLoginFails) throw new Error('no such account')
        state.demoLogins.push(email)
    },
}))

vi.mock('~/composables/useConsentGuard', () => ({
    useConsentGuard: () => ({needsReconsent: {value: state.needsReconsent}}),
}))

vi.mock('~/composables/useSession', () => ({
    useSession: () => ({
        loaded: {value: state.sessionLoaded},
        load: async () => {
            state.sessionLoads++
            state.sessionLoaded = true
        },
        isAdmin: () => state.admin,
        clear: () => {
            state.sessionCleared++
        },
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
        state.needsReconsent = false
        state.demo = {demo: false, dev: false}
        state.demoLogins = []
        state.demoLoginFails = false
        state.sessionCleared = 0
        localStorage.clear()
    })

    describe('signing in through a link', () => {
        it('becomes the named account on a development instance and drops the parameter', async () => {
            state.demo = {demo: false, dev: true}
            state.store.set('station_id', 'somebody-elses-station')

            await run(route('/station/events/upcoming', {as: 'tim@berger.local', station: STATION}))

            expect(state.demoLogins).toEqual(['tim@berger.local'])
            expect(state.sessionCleared).toBe(1)
            expect(state.store.has('station_id')).toBe(false)
            expect(state.navigations).toEqual([
                {path: '/station/events/upcoming', query: {station: STATION}, hash: undefined, replace: true},
            ])
        })

        it('leaves a production instance alone and still drops the parameter', async () => {
            state.demo = {demo: false, dev: false}

            await run(route('/station/dashboard/overview', {as: 'tim@berger.local', station: STATION}))

            expect(state.demoLogins).toEqual([])
            expect(state.navigations).toEqual([
                {path: '/station/dashboard/overview', query: {station: STATION}, hash: undefined, replace: true},
            ])
        })

        it('sends somebody to the login page when the account is not there', async () => {
            state.demo = {demo: true, dev: false}
            state.demoLoginFails = true

            await run(route('/station/dashboard/overview', {as: 'niemand@nirgends.local'}))

            expect(state.navigations).toEqual([
                {path: '/login', query: {redirect: '/station/dashboard/overview'}},
            ])
        })

        it('does nothing at all without the parameter', async () => {
            state.demo = {demo: false, dev: true}

            await run(route('/station/dashboard/overview', {station: STATION}))

            expect(state.demoLogins).toEqual([])
        })
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
     * The reported hang: consent out of date and an idle session set the two gates on each other.
     * Consent sent every page to {@code /reconsent}, the idle check sent that to the requirements,
     * consent sent it back, and neither writes the activity stamp, so nothing ever closed the idle
     * window. The tab spun until the browser called the page unresponsive, and only a reload broke
     * it, because the consent flag lives no longer than the page does.
     */
    it('lets an idle session reach the consent page instead of bouncing it away', async () => {
        state.store.set('station_id', STATION)
        state.needsReconsent = true
        idleForAnHour()

        await run(route('/reconsent'))

        expect(state.navigations, 'the consent page is where it was sent, so it is left alone').toEqual([])
    })

    it('still sends an idle session with stale consent to the consent page', async () => {
        state.store.set('station_id', STATION)
        state.needsReconsent = true
        idleForAnHour()

        await run(route('/station/dashboard/overview'))

        expect(state.navigations).toEqual(['/reconsent'])
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
