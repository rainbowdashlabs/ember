/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {findEmbeddedEvent} from './embeddedEventLookup'
import {sessionInfo} from '@/util/sessionState'
import {createSessionInfo} from '@/test/mocks/factories'

const listPublicEvents = vi.fn()
const getEmbeddedEvent = vi.fn()

vi.mock('@/api/publicEvents', () => ({listPublicEvents: (uid: string) => listPublicEvents(uid)}))
vi.mock('@/api/events', () => ({getEmbeddedEvent: (uid: string) => getEmbeddedEvent(uid)}))

const STATION = 'station-a'
const EVENT_UID = 'event-1'

function signedInTo(stationId: string) {
    sessionInfo.value = createSessionInfo({stationId})
}

function memberEvent(id: number, name: string, cancelled = false) {
    return {id, name, description: null, startTime: null, endTime: null, cancelled, categoryName: null}
}

/**
 * How an event block finds its event: the public list for everybody and on every public page, the
 * member lookup only in a news or wiki article and only for a member of the station that owns it.
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

    it('finds a public event for a reader outside the station through the public list', async () => {
        listPublicEvents.mockResolvedValue([{id: 1, publicUid: EVENT_UID, name: 'Sommerfest', categoryName: 'Feste'}])

        const found = await findEmbeddedEvent(STATION, EVENT_UID, 'MEMBERS')

        expect(found?.name).toBe('Sommerfest')
        expect(found?.source).toEqual({kind: 'PUBLIC', stationUid: STATION})
        expect(getEmbeddedEvent).not.toHaveBeenCalled()
    })

    it('asks the station itself for a member reading an article, even about a public event', async () => {
        signedInTo(STATION)
        listPublicEvents.mockResolvedValue([{id: 1, publicUid: EVENT_UID, name: 'Sommerfest'}])
        getEmbeddedEvent.mockResolvedValue(memberEvent(9, 'Sommerfest'))

        expect((await findEmbeddedEvent(STATION, EVENT_UID, 'MEMBERS'))?.source).toEqual({kind: 'MEMBER', eventId: 9})
        expect(listPublicEvents).not.toHaveBeenCalled()
    })

    it('asks a member reading an article about an internal event', async () => {
        signedInTo(STATION)
        getEmbeddedEvent.mockResolvedValue(memberEvent(42, 'Dienstabend', true))

        const found = await findEmbeddedEvent(STATION, EVENT_UID, 'MEMBERS')

        expect(found?.name).toBe('Dienstabend')
        expect(found?.cancelled).toBe(true)
        expect(found?.source).toEqual({kind: 'MEMBER', eventId: 42})
    })

    /** A page is read by anybody, so what it shows cannot depend on who happens to be signed in. */
    it('shows a member on a public page only what the public list shows', async () => {
        signedInTo(STATION)
        getEmbeddedEvent.mockResolvedValue(memberEvent(42, 'Dienstabend'))

        expect(await findEmbeddedEvent(STATION, EVENT_UID, 'PUBLIC')).toBeNull()
        expect(getEmbeddedEvent).not.toHaveBeenCalled()
    })

    it('does not ask about another station\'s internal event', async () => {
        signedInTo('station-b')

        expect(await findEmbeddedEvent(STATION, EVENT_UID, 'MEMBERS')).toBeNull()
        expect(getEmbeddedEvent).not.toHaveBeenCalled()
    })

    it('finds nothing where not every member may see the event', async () => {
        signedInTo(STATION)
        getEmbeddedEvent.mockRejectedValue(new Error('404'))

        expect(await findEmbeddedEvent(STATION, EVENT_UID, 'MEMBERS')).toBeNull()
    })

    it('finds nothing for an outside reader when the public list cannot be read', async () => {
        listPublicEvents.mockRejectedValue(new Error('offline'))

        expect(await findEmbeddedEvent(STATION, EVENT_UID, 'PUBLIC')).toBeNull()
    })
})
