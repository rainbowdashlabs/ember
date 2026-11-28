/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {LendingStatus, type LentOutItem} from '@/api/generated/schema'

/**
 * The pieces loans to partner stations hold today, each with the station it is set aside for.
 *
 * <p>A piece is out only while it is gone: an agreed loan holds it from its first day to its last,
 * nothing before, and gear handed over is held until it is back, whatever the days said. The server
 * refuses acting on a held piece by the same rule.
 *
 * @param loans the loans of an inventory, as the lending screen lists them
 * @param today the day to read them on, as an ISO date
 * @return the borrowing station's name per held piece
 */
export function piecesHeldByLoans(loans: readonly LentOutItem[], today: string): ReadonlyMap<number, string> {
    const held = new Map<number, string>()
    for (const loan of loans) {
        if (loan.assignedItemId == null || !holdsToday(loan, today)) continue
        held.set(loan.assignedItemId, loan.requestingStationName)
    }
    return held
}

function holdsToday(loan: LentOutItem, today: string): boolean {
    if (loan.status === LendingStatus.LENT) return true
    return loan.status === LendingStatus.APPROVED && loan.dateFrom <= today && loan.dateTo >= today
}
