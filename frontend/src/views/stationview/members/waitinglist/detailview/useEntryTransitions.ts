/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import { waitingList } from '@/api'
import type { WaitingListEntryWithScore } from '@/api/waitingList'
import { StationPermission } from '@/api/types'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useSession } from '@/composables/useSession'
import { useSidebarCounts } from '@/composables/useSidebarCounts'

export type TransitionKind = 'testing' | 'join' | 'approve' | 'reject' | 'withdraw' | 'backToWaiting'

export interface PendingTransition {
  entry: WaitingListEntryWithScore
  kind: TransitionKind
}

/**
 * Moving a waiting-list entry to its next state.
 *
 * Every move is confirmed before it runs - each one is visible to the applicant, and joining in
 * particular creates a member. Joining therefore also hands over to the member editor when the
 * acting user may edit members, so the new member can be completed straight away.
 *
 * Inviting is not among them. It is the one move with something to fill in, so it has a screen of
 * its own rather than a line of confirmation text.
 *
 * @param listId  the list being worked on
 * @param entries the entry list, reloaded after each move
 * @param error   the view's error channel
 */
export function useEntryTransitions(
  listId: Ref<number>,
  entries: Ref<WaitingListEntryWithScore[]>,
  error: Ref<string>,
) {
  const router = useRouter()
  const { hasPermission } = useSession()
  const { refresh: refreshSidebarCounts } = useSidebarCounts()

  const pending = ref<PendingTransition | null>(null)

  function request(entryId: number, kind: TransitionKind) {
    const entry = entries.value.find(e => e.entry.id === entryId)
    if (!entry) return
    pending.value = {entry, kind}
  }

  const { running, error: transitionError, run: confirm } = useAsyncAction(async () => {
    if (!pending.value) return
    const { entry, kind } = pending.value
    const entryId = entry.entry.id
    error.value = ''

    if (kind === 'join') {
      const result = await waitingList.moveToJoined(listId.value, entryId)
      refreshSidebarCounts()
      pending.value = null
      if (result.memberId && hasPermission(StationPermission.MEMBER_EDIT)) {
        router.push({name: 'members-edit', params: {id: result.memberId}})
        return
      }
    } else if (kind === 'backToWaiting') {
      await waitingList.returnToWaiting(listId.value, entryId)
    } else if (kind === 'testing') {
      await waitingList.moveToTesting(listId.value, entryId)
    } else if (kind === 'approve') {
      await waitingList.approveEntry(listId.value, entryId)
    } else if (kind === 'reject') {
      await waitingList.rejectEntry(listId.value, entryId)
    } else if (kind === 'withdraw') {
      await waitingList.withdrawEntry(listId.value, entryId)
    }

    entries.value = await waitingList.listEntries(listId.value)
    refreshSidebarCounts()
    pending.value = null
  })

  return {
    pending,
    running,
    error: transitionError,
    confirm,
    backToWaiting: (id: number) => request(id, 'backToWaiting'),
    moveToTesting: (id: number) => request(id, 'testing'),
    moveToJoined: (id: number) => request(id, 'join'),
    approve: (id: number) => request(id, 'approve'),
    reject: (id: number) => request(id, 'reject'),
    withdraw: (id: number) => request(id, 'withdraw'),
  }
}
