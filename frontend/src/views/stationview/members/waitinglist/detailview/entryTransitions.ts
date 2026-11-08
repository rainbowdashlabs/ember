/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {WaitingListEntryWithScore} from '@/api/generated/schema'

/** A move a waiting list entry can make that is confirmed with a line of text rather than a form. */
export type TransitionKind = 'testing' | 'join' | 'approve' | 'reject' | 'withdraw' | 'backToWaiting'

/** A move asked for and waiting for its confirmation. */
export interface PendingTransition {
  entry: WaitingListEntryWithScore
  kind: TransitionKind
}

/** The moves the family is written to about, each with a message the station can add. */
export const TELLS_THE_FAMILY: ReadonlySet<TransitionKind> = new Set<TransitionKind>(['testing', 'withdraw'])
