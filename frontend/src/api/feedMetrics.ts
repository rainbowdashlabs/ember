/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/**
 * Daily aggregate of feed renders for one (type, status) pair. The five
 * `bucket*` fields form a fixed histogram of render durations in ms:
 *
 * - bucketLt50    → duration in [0, 50) ms
 * - bucketLt200   → duration in [50, 200) ms
 * - bucketLt1000  → duration in [200, 1000) ms
 * - bucketLt5000  → duration in [1000, 5000) ms
 * - bucketGte5000 → duration ≥ 5000 ms
 *
 * Together they sum to `count`, so the chart can show "how fast is the feed?".
 */
export interface FeedMetricDaily {
    day: string
    type: string
    status: number
    count: number
    totalDurationMs: number
    totalEntries: number
    bucketLt50: number
    bucketLt200: number
    bucketLt1000: number
    bucketLt5000: number
    bucketGte5000: number
}

export interface FeedUserAgentStat {
    uaHash: string
    uaString: string
    requestCount: number
    firstSeen: string
    lastSeen: string
}

export interface FeedUserAgentsResponse {
    totalRequests: number
    userAgents: FeedUserAgentStat[]
}

export async function getDailyMetrics(days = 30): Promise<FeedMetricDaily[]> {
    const res = await client.get<FeedMetricDaily[]>('/admin/feed-metrics', {params: {days}})
    return res.data
}

export async function getUserAgents(limit = 50): Promise<FeedUserAgentsResponse> {
    const res = await client.get<FeedUserAgentsResponse>('/admin/feed-metrics/user-agents', {params: {limit}})
    return res.data
}
