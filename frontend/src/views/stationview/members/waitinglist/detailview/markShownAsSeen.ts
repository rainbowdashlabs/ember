/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {waitingList} from '@/api'
import type {WaitingListEntryWithScore} from '@/api/generated/schema'

/**
 * Takes the entries a list page shows off the reader's sidebar count. Only up to the newest one
 * shown, so an entry that arrived since the list was read still counts. A failure leaves the count
 * as it was, which is not worth an error on the page.
 *
 * @param listId the list on the page
 * @param shown  the entries it shows
 */
export function markShownAsSeen(listId: number, shown: readonly WaitingListEntryWithScore[]): void {
    const newest = Math.max(0, ...shown.map(row => row.entry.id))
    if (newest > 0) waitingList.markSeen(listId, newest).catch(() => {})
}
