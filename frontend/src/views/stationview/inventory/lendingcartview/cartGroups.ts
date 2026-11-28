/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {CartLineCheck, CartLineView} from '@/api/generated/schema'

/** The lines of one lending station, which go out together as one request. */
export interface CartStationGroup {
    stationId: string
    stationName: string
    lines: CartLineView[]
}

/** The cart's lines by lending station, in the order the stations were first put in. */
export function groupByStation(lines: readonly CartLineView[]): CartStationGroup[] {
    const groups = new Map<string, CartStationGroup>()
    for (const line of lines) {
        const group = groups.get(line.stationId) ?? {stationId: line.stationId, stationName: line.stationName, lines: []}
        group.lines.push(line)
        groups.set(line.stationId, group)
    }
    return [...groups.values()]
}

/** Why the cart cannot be sent yet, or null where it can. */
export type CheckoutBlock = 'empty' | 'noDay' | 'unchecked' | 'notFree' | null

/**
 * Whether the cart can be sent: it holds something, has a first day, and every line was found free in
 * full by the last check.
 */
export function checkoutBlock(
    lines: readonly CartLineView[], dateFrom: string | null, checks: ReadonlyMap<number, CartLineCheck> | null,
): CheckoutBlock {
    if (lines.length === 0) return 'empty'
    if (!dateFrom) return 'noDay'
    if (!checks || lines.some(line => !checks.has(line.id))) return 'unchecked'
    return lines.every(line => checks.get(line.id)?.state === 'FREE') ? null : 'notFree'
}
