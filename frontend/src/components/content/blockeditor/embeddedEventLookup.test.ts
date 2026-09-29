/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {findEmbeddedEvent} from './embeddedEventLookup'
import {sessionInfo} from '@/util/sessionState'
import type {SessionInfo} from '@/api/types'

const listPublicEvents = vi.fn()
const getEmbeddedEvent = vi.fn()

vi.mock('@/api/publicEvents', () => ({listPublicEvents: (uid: string) => listPublicEvents(uid)}))
vi.mock('@/api/events', () => ({getEmbeddedEvent: (uid: string) => getEmbeddedEvent(uid)}))

const STATION = 'station-a'
const EVENT_UID = 'event-1'

function signedInTo(stationId: string) {
    sessionInfo.value = {stationId} as SessionInfo
}

/**
 * How an event block finds its event: the public list for everybody, the member lookup only for a
 * member of the station that owns it, and nothing at all for anybody else.
 */
describe('findEmbeddedEvent', () => {
    beforeEach(() => {
        listPublicEvents.mockReset()
        getEmbeddedEvent.mockReset()
        listPublicEvents.mockResolvedValue([])
    })

    afterEach(() => {
        sessionInfo.value = null
    })

    it('finds a public event for any reader and links to its public page', async () => {
        listPublicEvents.mockResolvedValue([{id: 1, publicUid: EVENT_UID, name: 'Sommerfest', categoryName: 'Feste'}])

        const found = await findEmbeddedEvent(STATION, EVENT_UID)

        expect(found?.name).toBe('Sommerfest')
        expect(found?.source).toEqual({kind: 'PUBLIC', stationUid: STATION, publicUid: EVENT_UID})
        expect(getEmbeddedEvent).not.toHaveBeenCalled()
    })

    it('asks a member of the owning station about an internal event', async () => {
        signedInTo(STATION)
        getEmbeddedEvent.mockResolvedValue({
            id: 42, name: 'Dienstabend', description: null, startTime: null, endTime: null,
            cancelled: true, categoryName: null,
        })

        const found = await findEmbeddedEvent(STATION, EVENT_UID)

        expect(found?.name).toBe('Dienstabend')
        expect(found?.cancelled).toBe(true)
        expect(found?.source).toEqual({kind: 'MEMBER', eventId: 42})
    })

    it('does not ask about another station\'s internal event', async () => {
        signedInTo('station-b')

        expect(await findEmbeddedEvent(STATION, EVENT_UID)).toBeNull()
        expect(getEmbeddedEvent).not.toHaveBeenCalled()
    })

    it('finds nothing where the member may not see the event', async () => {
        signedInTo(STATION)
        getEmbeddedEvent.mockRejectedValue(new Error('404'))

        expect(await findEmbeddedEvent(STATION, EVENT_UID)).toBeNull()
    })

    it('falls through to the member lookup when the public list cannot be read', async () => {
        signedInTo(STATION)
        listPublicEvents.mockRejectedValue(new Error('offline'))
        getEmbeddedEvent.mockResolvedValue({
            id: 5, name: 'Übung', description: 'Kurz', startTime: null, endTime: null,
            cancelled: false, categoryName: null,
        })

        expect((await findEmbeddedEvent(STATION, EVENT_UID))?.description).toBe('Kurz')
    })
})
