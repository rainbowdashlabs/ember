/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getEmbeddedEvent} from '@/api/events'
import {listPublicEvents} from '@/api/publicEvents'
import {sessionInfo} from '@/util/sessionState'

/** Where an event block found its event, which decides where its link goes. */
export type EmbeddedEventSource =
    | {kind: 'PUBLIC', stationUid: string, publicUid: string}
    | {kind: 'MEMBER', eventId: number}

/** An event as an event block shows it, whichever way it was found. */
export interface FoundEvent {
    name: string
    description: string
    startTime: string | null
    endTime: string | null
    categoryName: string | null
    cancelled: boolean
    source: EmbeddedEventSource
}

/**
 * Finds the event a block names by its public id.
 *
 * <p>The public list comes first, because it is what every reader can ask: the public blog, a
 * partner station and a signed-in member alike. Only a member signed in to the station that owns
 * the event is asked about the rest, and the server answers that only for an event they may see.
 * Everybody else gets nothing, and the block says the event is not available here.
 *
 * @param stationUid the station the block belongs to
 * @param eventUid   the public id the block names
 */
export async function findEmbeddedEvent(stationUid: string, eventUid: string): Promise<FoundEvent | null> {
    const listed = await findPublicEvent(stationUid, eventUid)
    if (listed) return listed
    if (sessionInfo.value?.stationId !== stationUid) return null
    try {
        const event = await getEmbeddedEvent(eventUid)
        return {
            name: event.name,
            description: event.description ?? '',
            startTime: event.startTime,
            endTime: event.endTime,
            categoryName: event.categoryName,
            cancelled: event.cancelled,
            source: {kind: 'MEMBER', eventId: event.id},
        }
    } catch {
        return null
    }
}

async function findPublicEvent(stationUid: string, eventUid: string): Promise<FoundEvent | null> {
    try {
        const match = (await listPublicEvents(stationUid)).find(e => e.publicUid === eventUid)
        if (!match) return null
        return {
            name: match.name,
            description: match.description ?? '',
            startTime: match.startTime ?? null,
            endTime: match.endTime ?? null,
            categoryName: match.categoryName ?? null,
            cancelled: false,
            source: {kind: 'PUBLIC', stationUid, publicUid: match.publicUid},
        }
    } catch {
        return null
    }
}
