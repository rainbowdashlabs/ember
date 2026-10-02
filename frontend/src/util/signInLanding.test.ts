/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {decideSignInLanding, FIRST_STATION_PATH} from '@/util/signInLanding'

const state = vi.hoisted(() => ({
    stations: [] as {memberId: number; stationId: string}[],
    info: null as {instanceUserType?: string} | null,
    needed: false,
    asked: 0,
}))

vi.mock('@/api', () => ({
    session: {
        getStations: async () => state.stations,
        getSessionInfo: async () => state.info,
    },
    clusters: {listMine: async () => []},
}))

vi.mock('@/api/stations', () => ({
    isFirstStationNeeded: async () => {
        state.asked++
        return state.needed
    },
}))

/**
 * Where a fresh session lands when the instance has no station at all yet.
 *
 * @vitest-environment happy-dom
 */
describe('decideSignInLanding', () => {
    beforeEach(() => {
        state.stations = []
        state.info = {instanceUserType: 'ADMINISTRATOR'}
        state.needed = true
        state.asked = 0
    })

    it('leads the administrator of an instance without stations to found the first one', async () => {
        expect(await decideSignInLanding()).toEqual({path: FIRST_STATION_PATH})
    })

    it('does so even where the administrator was headed somewhere else', async () => {
        expect((await decideSignInLanding('/admin/settings')).path).toBe(FIRST_STATION_PATH)
    })

    it('leaves an administrator on the dashboard once the instance has a station', async () => {
        state.needed = false

        expect((await decideSignInLanding()).path).toBe('/admin/dashboard/overview')
    })

    it('does not ask for anybody who belongs to a station or does not administer the instance', async () => {
        state.stations = [{memberId: 1, stationId: 's'}]
        await decideSignInLanding()
        state.stations = []
        state.info = {instanceUserType: 'USER'}
        const landing = await decideSignInLanding()

        expect(state.asked).toBe(0)
        expect(landing.path).toBe('/account')
    })
})
