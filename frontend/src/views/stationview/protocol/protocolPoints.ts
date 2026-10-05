/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {TestProtocolItem} from '@/api/generated/schema'

/**
 * The points a set of protocol points can reach at most. A bonus point adds to the score when it is
 * checked but never to the maximum, so it is left out here.
 */
export function maxPointsOf(items: readonly TestProtocolItem[]): number {
    return items.reduce((sum, item) => sum + (item.bonus ? 0 : item.points), 0)
}

/** The points reached with the given checks, bonus points included. */
export function scoreOf(items: readonly TestProtocolItem[], isChecked: (itemId: number) => boolean): number {
    return items.reduce((sum, item) => sum + (isChecked(item.id) ? item.points : 0), 0)
}
