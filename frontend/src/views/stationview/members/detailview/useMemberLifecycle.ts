/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { MyInventoryItem } from '@/api/inventory'
import {StationUserType, type StationMember} from '@/api/types'
import { stationMembers } from '@/api'
import { useAsyncAction } from '@/composables/useAsyncAction'

/**
 * Owns retiring the viewed member: whether they may be marked as a former
 * member at all, plus the mark former and delete actions themselves.
 */
export function useMemberLifecycle(
    memberId: Ref<number>,
    member: Ref<StationMember | null>,
    memberUserType: Ref<string>,
    memberInventory: Ref<MyInventoryItem[]>,
    error: Ref<string>,
) {
  const { t } = useI18n()

  const formerSuccess = ref(false)
  const deleteSuccess = ref(false)

  const formerBlockReasons = computed(() => {
    const reasons: string[] = []
    if (memberInventory.value.length > 0) {
      reasons.push(t('memberDetail.formerBlockInventory', { count: memberInventory.value.length }))
    }
    const forbidden: string[] = [StationUserType.GUARDIAN, StationUserType.MANAGER]
    if (forbidden.includes(memberUserType.value)) {
      reasons.push(t('memberDetail.formerBlockRole'))
    }
    return reasons
  })

  const canMarkFormer = computed(() => formerBlockReasons.value.length === 0 && !!member.value)

  const { running: markingFormer, error: formerError, run: markFormer } = useAsyncAction(async () => {
    error.value = ''
    await stationMembers.markFormer(memberId.value)
    formerSuccess.value = true
  }, { formatError: () => t('common.error') })

  const { running: deletingMember, error: deleteError, run: deleteMember } = useAsyncAction(async () => {
    error.value = ''
    await stationMembers.deleteMember(memberId.value)
    deleteSuccess.value = true
  }, { formatError: () => t('common.error') })

  return {
    formerSuccess,
    deleteSuccess,
    formerBlockReasons,
    canMarkFormer,
    markingFormer,
    formerError,
    markFormer,
    deletingMember,
    deleteError,
    deleteMember,
  }
}
