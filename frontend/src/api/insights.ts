/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {LeaderboardResponse, PageDetailResponse} from './generated/schema'

export interface LeaderboardQuery {
    from: string
    to: string
    limit?: number
}

export interface PageDetailQuery {
    from: string
    to: string
}

/**
 * Per-station leaderboard of public pages by hits in the given window. Bot hits are counted apart so the
 * dashboard can offer a bot toggle without asking again.
 */
export async function getLeaderboard(query: LeaderboardQuery): Promise<LeaderboardResponse> {
    const res = await client.get<LeaderboardResponse>('/station/insights/pages', {params: query})
    return res.data
}

/**
 * Drill-down for one public page: hourly, country and referrer breakdowns. The hourly series comes once
 * without bots and once with them, so the chart can switch without a round trip.
 */
export async function getPageDetail(pageId: number, query: PageDetailQuery): Promise<PageDetailResponse> {
    const res = await client.get<PageDetailResponse>(`/station/insights/pages/${pageId}`, {params: query})
    return res.data
}
