/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {FeedUse} from '@/api/feedToken'

const ACTIVE_WINDOW_MS = 7 * 24 * 3600_000

/** The later of the two fetch stamps, or null where neither feed has ever been fetched. */
export function lastFetched(use: FeedUse): string | null {
    const stamps = [use.icalPolledAt, use.notificationPolledAt].filter((s): s is string => !!s)
    if (stamps.length === 0) return null
    return stamps.reduce((latest, stamp) => (stamp > latest ? stamp : latest))
}

/** Whether anything has fetched either feed within the last week. */
export function usedRecently(use: FeedUse, now = Date.now()): boolean {
    const stamp = lastFetched(use)
    return !!stamp && now - new Date(stamp).getTime() <= ACTIVE_WINDOW_MS
}
