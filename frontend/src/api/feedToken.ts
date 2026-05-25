/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface FeedTokenResponse {
    token: string
    createdAt: string
}

export async function getFeedToken(): Promise<FeedTokenResponse | null> {
    try {
        const res = await client.get<FeedTokenResponse>('/feed/token')
        return res.data
    } catch (e: any) {
        if (e?.response?.status === 404) return null
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

export function buildFeedUrl(token: string, type: 'ical' | 'rss' | 'atom'): string {
    const base = client.defaults.baseURL ?? ''
    const file = type === 'ical' ? 'events.ics' : type === 'rss' ? 'notifications.rss' : 'notifications.atom'
    return `${window.location.origin}${base}/public/feed/${token}/${file}`
}
