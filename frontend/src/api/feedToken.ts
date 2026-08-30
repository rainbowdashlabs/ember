/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {apiErrorStatus} from '@/util/apiError'
import type {MemberIdentity} from '@/api/types'

export interface FeedTokenResponse {
    token: string
    createdAt: string
    icalPolledAt?: string | null
    notificationPolledAt?: string | null
}

export interface FeedStatusResponse {
    hasToken: boolean
    icalActive: boolean
    notificationActive: boolean
}

export async function getFeedToken(): Promise<FeedTokenResponse | null> {
    try {
        const res = await client.get<FeedTokenResponse>('/feed/token')
        return res.data
    } catch (e) {
        if (apiErrorStatus(e) === 404) return null
        throw e
    }
}

export async function createFeedToken(): Promise<FeedTokenResponse> {
    const res = await client.post<FeedTokenResponse>('/feed/token')
    return res.data
}

export async function regenerateFeedToken(): Promise<FeedTokenResponse> {
    const res = await client.post<FeedTokenResponse>('/feed/token/regenerate')
    return res.data
}

export async function revokeFeedToken(): Promise<void> {
    await client.delete('/feed/token')
}

export async function getFeedStatus(): Promise<FeedStatusResponse> {
    const res = await client.get<FeedStatusResponse>('/feed/token/status')
    return res.data
}

/**
 * One member's standing subscription as the station's monitoring page sees it. Never carries the
 * token: that is the whole key to one person's calendar.
 */
export interface FeedUse {
    memberId: number
    identity: MemberIdentity
    createdAt: string
    icalPolledAt?: string | null
    notificationPolledAt?: string | null
}

export async function getStationFeedUse(): Promise<FeedUse[]> {
    const res = await client.get<FeedUse[]>('/station/monitoring/feeds')
    return res.data
}

/**
 * Verbosity preset that maps to the backend's `?verbose` / `?images` query parameters:
 * - `rich` - full body, embedded imagery, MediaRSS thumbnails (default)
 * - `compact` - headline + deep link only, still keeps imagery for readers that surface it
 * - `minimal` - headline + link only, no inline images or MediaRSS modules
 *
 * The backend treats missing params as "rich", so we only emit the params that override
 * the default.
 */
export type FeedPreset = 'rich' | 'compact' | 'minimal'

export function buildFeedUrl(token: string, type: 'ical' | 'rss' | 'atom', preset: FeedPreset = 'rich'): string {
    const base = client.defaults.baseURL ?? ''
    const file = type === 'ical' ? 'events.ics' : type === 'rss' ? 'notifications.rss' : 'notifications.atom'
    const url = `${window.location.origin}${base}/public/feed/${token}/${file}`
    if (preset === 'rich') return url
    const params = new URLSearchParams()
    if (preset === 'compact') params.set('verbose', '0')
    if (preset === 'minimal') {
        params.set('verbose', '0')
        params.set('images', '0')
    }
    return `${url}?${params.toString()}`
}
