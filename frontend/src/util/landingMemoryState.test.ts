/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it} from 'vitest'
import {acceptStorage, setItem} from '@/api/storage'
import {sessionInfo} from '@/util/sessionState'
import {
    claimVisitedArea,
    forgetLandingMemory,
    readLandingMemory,
    rememberVisitedArea,
} from '@/util/landingMemoryState'

const STATION = '11111111-1111-4111-a111-111111111111'
const CLUSTER = '22222222-2222-4222-a222-222222222222'

function signedInAs(uid: string) {
    sessionInfo.value = {account: {id: 7, uid}}
}

describe('the last area somebody was in', () => {
    beforeEach(() => {
        localStorage.clear()
        acceptStorage()
        sessionInfo.value = null
        claimVisitedArea()
        forgetLandingMemory()
    })

    it('notes the station a page belongs to, and whose it is', () => {
        signedInAs('anna')
        setItem('station_id', STATION)

        rememberVisitedArea('/station/members')

        expect(readLandingMemory()).toEqual({area: 'station', account: 'anna', stationId: STATION})
    })

    it('notes the association and the administration area', () => {
        signedInAs('anna')
        setItem('cluster_id', CLUSTER)

        rememberVisitedArea('/cluster/stations')
        expect(readLandingMemory()).toEqual({area: 'cluster', account: 'anna', clusterUid: CLUSTER})

        rememberVisitedArea('/admin/dashboard/overview')
        expect(readLandingMemory()).toEqual({area: 'admin', account: 'anna'})
    })

    it('keeps the page out of it, only the area', () => {
        signedInAs('anna')
        setItem('station_id', STATION)

        rememberVisitedArea('/station/events/12?tab=list')

        expect(readLandingMemory()).toEqual({area: 'station', account: 'anna', stationId: STATION})
    })

    it('notes nothing for a page outside the three areas', () => {
        signedInAs('anna')

        rememberVisitedArea('/account/security')

        expect(readLandingMemory()).toBeNull()
    })

    /**
     * A page opened straight at a station navigates before the session has answered. What it notes
     * has no name yet, and an unnamed note is worthless: it is kept in the page until the session
     * says who is asking.
     */
    it('waits for the session before writing what it noted', () => {
        setItem('station_id', STATION)
        rememberVisitedArea('/station/dashboard/overview')
        expect(readLandingMemory()).toBeNull()

        signedInAs('anna')
        claimVisitedArea()

        expect(readLandingMemory()).toEqual({area: 'station', account: 'anna', stationId: STATION})
    })

    it('forgets everything when the session ends', () => {
        signedInAs('anna')
        setItem('station_id', STATION)
        rememberVisitedArea('/station/members')

        forgetLandingMemory()

        expect(readLandingMemory()).toBeNull()
    })

    it('refuses a note that was damaged in storage', () => {
        localStorage.setItem('landing_area', 'not json at all')
        expect(readLandingMemory()).toBeNull()

        localStorage.setItem('landing_area', JSON.stringify({area: 'moon', account: 'anna'}))
        expect(readLandingMemory()).toBeNull()

        localStorage.setItem('landing_area', JSON.stringify({area: 'station', stationId: STATION}))
        expect(readLandingMemory()).toBeNull()
    })
})
