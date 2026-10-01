/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {FeedMetricDaily, UserAgentsResponse} from './generated/schema'

/**
 * Daily aggregates of feed renders, one per (type, status) pair. The five `bucket*` fields form a fixed
 * histogram of render durations in ms that sums to `count`, so the chart can show how fast the feed is.
 */
export async function getDailyMetrics(days = 30): Promise<FeedMetricDaily[]> {
    const res = await client.get<FeedMetricDaily[]>('/admin/feed-metrics', {params: {days}})
    return res.data
}

export async function getUserAgents(limit = 50): Promise<UserAgentsResponse> {
    const res = await client.get<UserAgentsResponse>('/admin/feed-metrics/user-agents', {params: {limit}})
    return res.data
}
