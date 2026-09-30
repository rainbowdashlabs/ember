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
    | {kind: 'PUBLIC', stationUid: string}
    | {kind: 'MEMBER', eventId: number}

/**
 * Where a reader outside the station goes to see a public event. There is no public page for a
 * single event, so every event block sends them to the station's public calendar.
 */
export function publicEventsAddress(stationUid: string): string {
    return `/public/station/${stationUid}/calendar`
}

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
 * <p>A member signed in to the station that owns the event asks the station itself, which answers
 * for public and internal events alike as long as they may see it, and links them to the event's
 * own page on the day the block names. Every other reader, the public blog and partner stations
 * included, can only ask the public list. Where neither answers, the block says the event is not
 * available here.
 *
 * @param stationUid the station the block belongs to
 * @param eventUid   the public id the block names
 */
export async function findEmbeddedEvent(stationUid: string, eventUid: string): Promise<FoundEvent | null> {
    if (sessionInfo.value?.stationId !== stationUid) return findPublicEvent(stationUid, eventUid)
    return (await findMemberEvent(eventUid)) ?? findPublicEvent(stationUid, eventUid)
}

async function findMemberEvent(eventUid: string): Promise<FoundEvent | null> {
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
            source: {kind: 'PUBLIC', stationUid},
        }
    } catch {
        return null
    }
}
