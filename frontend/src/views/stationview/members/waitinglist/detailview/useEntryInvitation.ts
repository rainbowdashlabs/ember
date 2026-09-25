/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {waitingList} from '@/api'
import {describeFailure, type Failure} from '@/util/failure'
import type {WaitingListEntryWithScore} from '@/api/waitingList'
import type {EventOccurrenceRef} from '@/api/events'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * Inviting somebody to come and look, which is the one transition with something to fill in.
 *
 * It is the station's first message of its own accord, so it carries the occasion it is about: an
 * appointment, the one date of it, and the time the person is asked to be there. Nobody is signed
 * up from any of that. They have not joined anything, so putting them on the attendee list would
 * make them part of an appointment they never agreed to.
 *
 * @param listId  the list being worked on
 * @param entries the entry list, reloaded once the invitation has gone out
 * @param failure the view's failure channel
 */
export function useEntryInvitation(
  listId: Ref<number>,
  entries: Ref<WaitingListEntryWithScore[]>,
  failure: Ref<Failure | null>,
) {
  const {t} = useI18n()

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

  /**
   * Sends the invitation, then fetches the list again.
   *
   * <p>The fetch is caught on its own, because the invitation has reached the person by then. A reader
   * told that inviting failed invites them again, and somebody who has never heard from this station
   * before gets two messages about the same evening.
   */
  const {running, failure: inviteFailure, run: confirm} = useAsyncAction(async () => {
    if (!target.value) return
    failure.value = null
    const picked = occurrence.value
    await waitingList.inviteEntry(listId.value, target.value.entry.id, picked
      ? {eventId: picked.eventId, date: picked.date, arrivalTime: arrivalTime.value || null}
      : null)
    target.value = null

    try {
      entries.value = await waitingList.listEntries(listId.value)
    } catch (e) {
      failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
    }
  })

  return {target, occurrence, arrivalTime, running, failure: inviteFailure, request, cancel, confirm}
}
