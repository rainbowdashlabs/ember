/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {waitingList} from '@/api'
import type {WaitingListEntryWithScore} from '@/api/waitingList'
import type {EventOccurrenceRef} from '@/api/events'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * Inviting somebody to come and look, which is the one transition with something to fill in.
 *
 * It is the station's first message of its own accord, so it carries the evening it is about: an
 * appointment, the one date of it, and the time the person is asked to be there. Nobody is signed
 * up from any of that. They have not joined anything, so putting them on the attendee list would
 * make them part of an evening they never agreed to.
 *
 * @param listId  the list being worked on
 * @param entries the entry list, reloaded once the invitation has gone out
 * @param error   the view's error channel
 */
export function useEntryInvitation(
  listId: Ref<number>,
  entries: Ref<WaitingListEntryWithScore[]>,
  error: Ref<string>,
) {
  const target = ref<WaitingListEntryWithScore | null>(null)
  const occurrence = ref<EventOccurrenceRef | null>(null)
  const arrivalTime = ref('')

  function request(entryId: number) {
    const entry = entries.value.find(e => e.entry.id === entryId)
    if (!entry) return
    occurrence.value = null
    arrivalTime.value = ''
    target.value = entry
  }

  function cancel() {
    target.value = null
  }

  const {running, error: inviteError, run: confirm} = useAsyncAction(async () => {
    if (!target.value) return
    error.value = ''
    const picked = occurrence.value
    await waitingList.inviteEntry(listId.value, target.value.entry.id, picked
      ? {eventId: picked.eventId, date: picked.date, arrivalTime: arrivalTime.value || null}
      : null)
    entries.value = await waitingList.listEntries(listId.value)
    target.value = null
  })

  return {target, occurrence, arrivalTime, running, error: inviteError, request, cancel, confirm}
}
