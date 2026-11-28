/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {LentOutItem} from '@/api/generated/schema'

const HOLDING_STATUSES: ReadonlySet<string> = new Set(['APPROVED', 'LENT'])

/**
 * The pieces loans to partner stations hold, each with the station it is set aside for.
 *
 * <p>A loan holds its pieces from its approval until its end date has passed, which is how the station
 * is refused for acting on one. A loan whose end date is behind it holds nothing, whatever its status
 * still shows.
 *
 * @param loans the loans of an inventory, as the lending screen lists them
 * @param today the day to read them on, as an ISO date
 * @return the borrowing station's name per held piece
 */
export function piecesHeldByLoans(loans: readonly LentOutItem[], today: string): ReadonlyMap<number, string> {
    const held = new Map<number, string>()
    for (const loan of loans) {
        if (loan.assignedItemId == null || !HOLDING_STATUSES.has(loan.status)) continue
        if (loan.dateTo != null && loan.dateTo < today) continue
        held.set(loan.assignedItemId, loan.requestingStationName)
    }
    return held
}
