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

/**
 * The share links that let people put themselves on a waiting list.
 *
 * A link may be limited by number of uses, by an expiry date, by both or by neither, so both
 * fields are optional and an empty one is sent as absent rather than as a zero or an empty date.
 *
 * @param listId  the list the links belong to
 * @param invites the link list, reloaded after every change
 * @param error   the view's error channel
 * @param flash   shows a transient confirmation, used when a link is copied
 */
export function useListInvites(
  listId: Ref<number>,
  invites: Ref<WaitingListInvite[]>,
  error: Ref<string>,
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

  const { running: creating, error: createError, run: create } = useAsyncAction(async () => {
    error.value = ''
    await waitingList.createInvite(listId.value, {
      maxUses: maxUses.value || undefined,
      expiresAt: expiresAt.value || undefined,
    })
    invites.value = await waitingList.listInvites(listId.value)
    showModal.value = false
  })

  async function remove(inviteId: number) {
    error.value = ''
    try {
      await waitingList.deleteInvite(listId.value, inviteId)
      invites.value = await waitingList.listInvites(listId.value)
    } catch {
      error.value = t('common.error')
    }
  }

  async function copyLink(code: string) {
    await navigator.clipboard.writeText(`${window.location.origin}/waiting-list/register?code=${code}`)
    flash(t('waitingList.linkCopied'))
  }

  return {showModal, maxUses, expiresAt, creating, createError, openModal, create, remove, copyLink}
}
