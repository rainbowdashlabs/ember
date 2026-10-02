/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {EventCategory, PublicEventResponse} from './generated/schema'

export async function listPublicEvents(stationUid: string): Promise<PublicEventResponse[]> {
    const res = await client.get<PublicEventResponse[]>(`/public/events/${stationUid}`)
    return res.data
}

export async function listPublicCategories(stationUid: string): Promise<EventCategory[]> {
    const res = await client.get<EventCategory[]>(`/public/events/${stationUid}/categories`)
    return res.data
}

/**
 * The calendar feed of a station, as a whole address a calendar application can be handed.
 *
 * The origin is only known in a browser. During a server render there is none, so the address is
 * given relative: reading it there would throw and take the whole page down with it.
 */
export function getIcalFeedUrl(stationUid: string): string {
    const path = `/api/v1/public/events/${stationUid}/feed/ical`
    return import.meta.client ? `${window.location.origin}${path}` : path
}

/** The same feed for a calendar application, which subscribes over its own scheme. */
export function getIcalSubscribeUrl(stationUid: string): string {
    if (!import.meta.client) return ''
    return `${window.location.origin}/api/v1/public/events/${stationUid}/feed/ical`
        .replace(/^https?:/, 'webcal:')
}
