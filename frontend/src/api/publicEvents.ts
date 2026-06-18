/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface PublicEvent {
    id: number
    /** Stable opaque public identifier — referenced by event cells (concept §2.3). */
    publicUid: string
    name: string
    description?: string
    eventType?: string
    dayOfWeek?: number | null
    startTime?: string
    endTime?: string
    categoryId?: number | null
    categoryName?: string
    publicFields?: { name: string; value: string; fieldType: string }[]
}

export interface PublicEventCategory {
    id: number
    name: string
}

export async function listPublicEvents(stationUid: string): Promise<PublicEvent[]> {
    const res = await client.get<PublicEvent[]>(`/public/events/${stationUid}`)
    return res.data
}

export async function listPublicCategories(stationUid: string): Promise<PublicEventCategory[]> {
    const res = await client.get<PublicEventCategory[]>(`/public/events/${stationUid}/categories`)
    return res.data
}

export async function getPublicEvent(stationUid: string, eventId: number): Promise<PublicEvent> {
    const res = await client.get<PublicEvent>(`/public/events/${stationUid}/${eventId}`)
    return res.data
}

export function getIcalFeedUrl(stationUid: string): string {
    return `${window.location.origin}/api/v1/public/events/${stationUid}/feed/ical`
}

export function getIcalSubscribeUrl(stationUid: string): string {
    const httpUrl = `${window.location.origin}/api/v1/public/events/${stationUid}/feed/ical`
    return httpUrl.replace(/^https?:/, 'webcal:')
}
