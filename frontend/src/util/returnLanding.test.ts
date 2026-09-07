/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {acceptStorage, setItem} from '@/api/storage'
import type {LandingMemory} from '@/util/landingMemoryState'
import {decideReturnLanding} from '@/util/returnLanding'

const STATION = '11111111-1111-4111-a111-111111111111'
const OTHER_STATION = '33333333-3333-4333-a333-333333333333'
const CLUSTER = '22222222-2222-4222-a222-222222222222'

const state = vi.hoisted(() => ({
    stations: [] as {memberId: number; stationId: string}[],
    clusters: [] as {uid: string; name: string}[],
    info: null as {account?: {id: number; uid?: string}; instanceUserType?: string} | null,
    memory: null as unknown,
}))

vi.mock('@/api', () => ({
    session: {
        getStations: async () => state.stations,
        getSessionInfo: async () => {
            if (!state.info) throw new Error('no session')
            return state.info
        },
    },
    clusters: {
        listMine: async () => state.clusters,
    },
}))

vi.mock('@/util/landingMemoryState', async (original) => ({
    ...await original<Record<string, unknown>>(),
    readLandingMemory: () => state.memory,
}))

function remembered(memory: LandingMemory) {
    state.memory = memory
}

describe('where somebody who is already signed in goes from the landing page', () => {
    beforeEach(() => {
        localStorage.clear()
        acceptStorage()
        setItem('session_token', 'a session')
        state.stations = [{memberId: 1, stationId: STATION}]
        state.clusters = []
        state.info = {account: {id: 7, uid: 'anna'}}
        state.memory = null
    })

    /** Nobody is sent anywhere from a page they came to read. */
    it('sends a visitor who is not signed in nowhere at all', async () => {
        localStorage.removeItem('session_token')
        remembered({area: 'station', account: 'anna', stationId: STATION})

        expect(await decideReturnLanding()).toBeNull()
    })

    it('follows what the membership says while nothing is remembered', async () => {
        expect(await decideReturnLanding()).toEqual({path: '/station/requirements', stationId: STATION})
    })

    it('returns to the administration area even where a station would have been the answer', async () => {
        state.info = {account: {id: 7, uid: 'anna'}, instanceUserType: 'ADMINISTRATOR'}
        remembered({area: 'admin', account: 'anna'})

        expect(await decideReturnLanding()).toEqual({path: '/admin/dashboard/overview'})
    })

    it('returns to the remembered station out of several', async () => {
        state.stations = [{memberId: 1, stationId: STATION}, {memberId: 2, stationId: OTHER_STATION}]
        remembered({area: 'station', account: 'anna', stationId: OTHER_STATION})

        expect(await decideReturnLanding())
            .toEqual({path: '/station/dashboard/overview', stationId: OTHER_STATION})
    })

    it('returns to the remembered association', async () => {
        state.stations = []
        state.clusters = [{uid: CLUSTER, name: 'Kreis'}]
        remembered({area: 'cluster', account: 'anna', clusterUid: CLUSTER})

        expect(await decideReturnLanding()).toEqual({path: '/cluster', clusterUid: CLUSTER})
    })

    /**
     * The whole point of checking: a note outlives the membership it names, and sending somebody to
     * a place that turns them away again is how the landing page becomes a roundabout.
     */
    describe('when what was remembered is no longer theirs', () => {
        it('falls back where the station is gone', async () => {
            remembered({area: 'station', account: 'anna', stationId: OTHER_STATION})

            expect(await decideReturnLanding()).toEqual({path: '/station/requirements', stationId: STATION})
        })

        it('falls back where the administration right was taken away', async () => {
            remembered({area: 'admin', account: 'anna'})

            expect(await decideReturnLanding()).toEqual({path: '/station/requirements', stationId: STATION})
        })

        it('falls back where the last association was left', async () => {
            state.stations = []
            remembered({area: 'cluster', account: 'anna', clusterUid: CLUSTER})

            expect(await decideReturnLanding()).toEqual({path: '/account'})
        })

        it('still offers the association area where another one remains', async () => {
            state.stations = []
            state.clusters = [{uid: 'a-different-one', name: 'Kreis'}]
            remembered({area: 'cluster', account: 'anna', clusterUid: CLUSTER})

            expect(await decideReturnLanding()).toEqual({path: '/cluster', clusterUid: undefined})
        })
    })

    it('ignores what somebody else left behind on this browser', async () => {
        state.stations = [{memberId: 1, stationId: STATION}, {memberId: 2, stationId: OTHER_STATION}]
        remembered({area: 'station', account: 'bert', stationId: OTHER_STATION})

        expect(await decideReturnLanding()).toEqual({path: '/cross-station'})
    })

    it('ignores it where the session no longer answers for anybody', async () => {
        state.stations = []
        state.info = null
        remembered({area: 'admin', account: 'anna'})

        expect(await decideReturnLanding()).toEqual({path: '/account'})
    })
})
