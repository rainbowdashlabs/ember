/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {WaitingListEntryWithScore} from '@/api/generated/schema'

/** The name a waiting list entry is shown by: first name, and the last name where one was given. */
export function entryFullName(item: WaitingListEntryWithScore): string {
    const e = item.entry
    return e.lastname ? `${e.firstname} ${e.lastname}` : e.firstname
}
