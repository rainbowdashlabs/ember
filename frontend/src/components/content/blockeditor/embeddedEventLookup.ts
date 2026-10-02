/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getEmbeddedEvent} from '@/api/events'
import {listPublicEvents} from '@/api/publicEvents'
import {useSession} from '@/composables/useSession'
import type {BlockAudience} from '@/api/generated/schema'

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
 * Finds the event a block names by its public id, as far as every reader of its content may see it.
 *
 * <p>In a news or wiki article (`MEMBERS`) a member signed in to the station that owns the event asks
 * the station itself, which answers for public and internal events alike as long as every member may
 * see it, and links them to the event's own page on the day the block names. On a public page, and
 * for every other reader, the public blog and partner stations included, only the public list is
 * asked, so a page never shows an internal event even to a member. Where neither answers, the block
 * says the event is not available here.
 *
 * @param stationUid the station the block belongs to
 * @param eventUid   the public id the block names
 * @param audience   who reads the content the block sits in
 */
export async function findEmbeddedEvent(
    stationUid: string,
    eventUid: string,
    audience: BlockAudience,
): Promise<FoundEvent | null> {
    const {sessionInfo} = useSession()
    if (audience !== 'MEMBERS' || sessionInfo.value?.stationId !== stationUid) {
        return findPublicEvent(stationUid, eventUid)
    }
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
