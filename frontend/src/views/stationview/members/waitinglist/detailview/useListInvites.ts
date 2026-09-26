/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { waitingList } from '@/api'
import type { WaitingListInvite } from '@/api/waitingList'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { describeFailure, type Failure } from '@/util/failure'

/**
 * The share links that let people put themselves on a waiting list.
 *
 * A link may be limited by number of uses, by an expiry date, by both or by neither, so both
 * fields are optional and an empty one is sent as absent rather than as a zero or an empty date.
 *
 * @param listId  the list the links belong to
 * @param invites the link list, reloaded after every change
 * @param failure the view's failure channel
 * @param flash   shows a transient confirmation, used when a link is copied
 */
export function useListInvites(
  listId: Ref<number>,
  invites: Ref<WaitingListInvite[]>,
  failure: Ref<Failure | null>,
  flash: (message: string) => void,
) {
  const { t } = useI18n()

  const showModal = ref(false)
  const maxUses = ref<number | undefined>(undefined)
  const expiresAt = ref('')

  function openModal() {
    maxUses.value = undefined
    expiresAt.value = ''
    showModal.value = true
  }

  const { running: creating, failure: createFailure, run: create } = useAsyncAction(async () => {
    failure.value = null
    await waitingList.createInvite(listId.value, {
      maxUses: maxUses.value || undefined,
      expiresAt: expiresAt.value || undefined,
    })
    showModal.value = false

    try {
      invites.value = await waitingList.listInvites(listId.value)
    } catch (e) {
      failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
    }
  })

  /**
   * Withdraws one link, then fetches the rest.
   *
   * <p>Caught apart, because the link stops working the moment the first call goes through. A reader
   * told otherwise sees it still listed and hunts for why it will not go away.
   */
  async function remove(inviteId: number) {
    failure.value = null
    try {
      await waitingList.deleteInvite(listId.value, inviteId)
    } catch (e) {
      failure.value = describeFailure(e, t)
      return
    }

    try {
      invites.value = await waitingList.listInvites(listId.value)
    } catch (e) {
      failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
    }
  }

  async function copyLink(code: string) {
    await navigator.clipboard.writeText(`${window.location.origin}/waiting-list/register?code=${code}`)
    flash(t('waitingList.linkCopied'))
  }

  return {showModal, maxUses, expiresAt, creating, createFailure, openModal, create, remove, copyLink}
}
