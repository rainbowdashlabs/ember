/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {waitingList} from '@/api'
import {describeFailure, type Failure} from '@/util/failure'
import type {InvitationRecipients, WaitingListEntryWithScore} from '@/api/generated/schema'
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
 * <p>Nothing goes out by accident. The appointment is required before it can be sent, and the
 * addresses it goes to are read first so the dialog can name them.
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
  const message = ref('')
  const recipients = ref<InvitationRecipients | null>(null)

  async function request(entryId: number) {
    const entry = entries.value.find(e => e.entry.id === entryId)
    if (!entry) return
    occurrence.value = null
    arrivalTime.value = ''
    message.value = ''
    recipients.value = null
    target.value = entry
    try {
      recipients.value = await waitingList.invitationRecipients(listId.value, entryId)
    } catch (e) {
      failure.value = describeFailure(e, t)
    }
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
    const picked = occurrence.value
    if (!target.value || !picked) return
    failure.value = null
    await waitingList.inviteEntry(listId.value, target.value.entry.id, {
      eventId: picked.eventId,
      date: picked.date,
      arrivalTime: arrivalTime.value || null,
      message: message.value.trim() || null,
    })
    target.value = null

    try {
      entries.value = await waitingList.listEntries(listId.value)
    } catch (e) {
      failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
    }
  })

  const canSend = computed(() => occurrence.value !== null && recipients.value !== null && !running.value)

  return {
    target,
    occurrence,
    arrivalTime,
    message,
    recipients,
    canSend,
    running,
    failure: inviteFailure,
    request,
    cancel,
    confirm,
  }
}
