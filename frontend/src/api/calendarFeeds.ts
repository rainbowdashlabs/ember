/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {CalendarFeedRequest, CalendarFeedResponse} from './generated/schema'

const feeds = createCrudResource<CalendarFeedResponse, CalendarFeedRequest>('/calendar-feeds')

/** The station's calendar feeds, oldest first. */
export const listCalendarFeeds = feeds.list
/** Creates a calendar feed with a fresh address. */
export const createCalendarFeed = feeds.create
/** Changes a feed's name, level and the categories it leaves out; the address stays. */
export const updateCalendarFeed = feeds.update
/** Deletes a feed, which ends its address. */
export const deleteCalendarFeed = feeds.remove

/** Gives a feed a new address; calendars holding the old one stop receiving it. */
export async function regenerateCalendarFeed(id: number): Promise<CalendarFeedResponse> {
    const res = await client.post<CalendarFeedResponse>(`/calendar-feeds/${id}/regenerate`)
    return res.data
}

/** The address a shared calendar subscribes to. */
export function stationFeedUrl(token: string): string {
    const base = client.defaults.baseURL ?? ''
    return `${window.location.origin}${base}/public/station-feed/${token}/events.ics`
}
