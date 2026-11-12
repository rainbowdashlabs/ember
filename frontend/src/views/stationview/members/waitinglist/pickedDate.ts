/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {WaitingListEntryWithScore} from '@/api/generated/schema'

/** One date an invitation offers. */
export type OfferedDate = NonNullable<WaitingListEntryWithScore['entry']['invitation']>['options'][number]

/**
 * The date an entry's answer picked out of the dates its invitation offers, or null where the answer
 * picked none: no answer yet, or one that is not coming.
 */
export function pickedOption(entry: WaitingListEntryWithScore['entry']): OfferedDate | null {
    const position = entry.answer?.position
    if (position === null || position === undefined) return null
    return entry.invitation?.options.find(option => option.position === position) ?? null
}
