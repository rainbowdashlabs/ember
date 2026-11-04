/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EventOccurrenceRef} from '@/api/events'
import type {WaitingListInvitationOptionRequest} from '@/api/generated/schema'

/** What the invitation dialog is doing: inviting somebody waiting, or replacing the dates somebody holds. */
export type InvitationMode = 'invite' | 'otherDates'

/** The most dates one invitation offers, as the backend refuses anything above it. */
export const MAX_INVITATION_DATES = 5

/**
 * One row of the invitation dialog: a date being picked and the time they are asked to be there.
 *
 * The id stays with the row however the others move, so removing a row above it does not hand its
 * picker over to a different date.
 */
export interface InvitationDateDraft {
  id: string
  occurrence: EventOccurrenceRef | null
  arrivalTime: string
}

/**
 * A row nothing has been picked in yet.
 *
 * The id is drawn rather than counted, because a counter at module level is shared by every request
 * the server renders, and `crypto.randomUUID` is missing on a station served over plain HTTP.
 */
export function emptyDate(): InvitationDateDraft {
  return {id: `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`, occurrence: null, arrivalTime: ''}
}

/** The rows with a date picked, in the order they stand, as the invitation request carries them. */
export function pickedDates(drafts: InvitationDateDraft[]): WaitingListInvitationOptionRequest[] {
  return drafts.flatMap(draft => draft.occurrence
    ? [{eventId: draft.occurrence.eventId, date: draft.occurrence.date, arrivalTime: draft.arrivalTime || null}]
    : [])
}

/**
 * Whether two rows name the same date of the same appointment, which the backend refuses because the
 * person could not tell the two apart.
 */
export function hasDuplicateDates(drafts: InvitationDateDraft[]): boolean {
  const seen = new Set<string>()
  for (const draft of drafts) {
    if (!draft.occurrence) continue
    const key = `${draft.occurrence.eventId}:${draft.occurrence.date}`
    if (seen.has(key)) return true
    seen.add(key)
  }
  return false
}
