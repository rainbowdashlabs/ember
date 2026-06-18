/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/**
 * One row of the public-page leaderboard for a station. Mirrors
 * {@code PageHitRepository.PageLeaderboardEntry}. {@code botHits} is reported
 * separately so the dashboard can offer a bot toggle without re-querying.
 */
export interface PageLeaderboardEntry {
    pageId: number
    title: string
    slug: string
    hits: number
    botHits: number
}

export interface LeaderboardResponse {
    rows: PageLeaderboardEntry[]
}

/** One time-series sample (hour bucket plus total hits across the station). */
export interface HourlyTotal {
    hour: string
    hits: number
}

/** One row of a dimension breakdown (country code or referer domain). */
export interface DimensionTotal {
    dimension: string
    hits: number
}

/**
 * Per-page drill-down response. {@code hourly} excludes bots; {@code hourlyWithBots}
 * is returned alongside so the chart can switch toggle without a round trip.
 */
export interface PageDetailResponse {
    hourly: HourlyTotal[]
    hourlyWithBots: HourlyTotal[]
    countries: DimensionTotal[]
    referrers: DimensionTotal[]
}

export interface LeaderboardQuery {
    from: string
    to: string
    limit?: number
}

export interface PageDetailQuery {
    from: string
    to: string
}

/** Per-station leaderboard of public pages by hits in the given window. */
export async function getLeaderboard(query: LeaderboardQuery): Promise<LeaderboardResponse> {
    const res = await client.get<LeaderboardResponse>('/station/insights/pages', {params: query})
    return res.data
}

/** Drill-down for one public page: hourly + country + referer breakdowns. */
export async function getPageDetail(pageId: number, query: PageDetailQuery): Promise<PageDetailResponse> {
    const res = await client.get<PageDetailResponse>(`/station/insights/pages/${pageId}`, {params: query})
    return res.data
}
