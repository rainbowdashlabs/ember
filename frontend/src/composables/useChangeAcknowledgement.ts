/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { profileFieldChanges } from '@/api'
import type { ProfileFieldChange } from '@/api/profileFieldChanges'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useSidebarCounts } from '@/composables/useSidebarCounts'

/**
 * Acknowledging profile field changes, shared by the station-wide review list and the change
 * history on a single member.
 *
 * An acknowledgement may carry a comment, which is why the comment box opens per change rather
 * than sitting on the form: only the change being acknowledged should collect one, and switching
 * to another change discards what was typed for the previous one.
 *
 * Every acknowledgement refreshes the sidebar counts, since the pending badge is what brought the
 * user here.
 *
 * @param currentMemberId the acting member, compared against the recorded acknowledgements
 * @param onAcknowledged  reloads whatever the caller is showing once a change is acknowledged
 */
export function useChangeAcknowledgement(
  currentMemberId: () => number,
  onAcknowledged: () => void | Promise<void>,
) {
  const { t } = useI18n()
  const { refresh: refreshSidebarCounts } = useSidebarCounts()

  const acknowledgeComment = ref('')
  const showCommentForChangeId: Ref<number | null> = ref(null)

  const {running: acknowledging, error, run: runAcknowledge} = useAsyncAction(
    async (work: () => Promise<void>) => {
      await work()
      refreshSidebarCounts()
      await onAcknowledged()
    },
    {formatError: () => t('common.error')},
  )

  function isAcknowledgedByMe(change: ProfileFieldChange): boolean {
    return change.acknowledgements.some(a => a.acknowledgedBy === currentMemberId())
  }

  function unacknowledgedCount(changes: ProfileFieldChange[]): number {
    return changes.filter(c => c.requiresAcknowledgement && !isAcknowledgedByMe(c)).length
  }

  function acknowledgeChange(changeId: number) {
    return runAcknowledge(async () => {
      const comment = showCommentForChangeId.value === changeId ? acknowledgeComment.value : undefined
      await profileFieldChanges.acknowledge(changeId, {comment})
      showCommentForChangeId.value = null
      acknowledgeComment.value = ''
    })
  }

  /**
   * Acknowledges every outstanding change of one member. The comment is only sent when the caller
   * offers a comment box for the bulk action; the review list does not.
   */
  function acknowledgeAll(memberId: number, withComment = false) {
    return runAcknowledge(async () => {
      const comment = withComment ? acknowledgeComment.value || undefined : undefined
      await profileFieldChanges.acknowledgeAll(memberId, {comment})
      if (withComment) acknowledgeComment.value = ''
    })
  }

  function toggleComment(changeId: number) {
    const reopening = showCommentForChangeId.value !== changeId
    showCommentForChangeId.value = reopening ? changeId : null
    acknowledgeComment.value = ''
  }

  return {
    acknowledgeComment,
    showCommentForChangeId,
    acknowledging,
    error,
    isAcknowledgedByMe,
    unacknowledgedCount,
    acknowledgeChange,
    acknowledgeAll,
    toggleComment,
  }
}
