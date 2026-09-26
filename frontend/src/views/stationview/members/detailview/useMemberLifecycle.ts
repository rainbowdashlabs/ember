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
import type { Failure } from '@/util/failure'

/**
 * Owns retiring the viewed member: whether they may be marked as a former
 * member at all, plus the mark former and delete actions themselves.
 */
export function useMemberLifecycle(
    memberId: Ref<number>,
    member: Ref<StationMember | null>,
    memberUserType: Ref<string>,
    memberInventory: Ref<MyInventoryItem[]>,
    failure: Ref<Failure | null>,
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

  const { running: markingFormer, failure: formerFailure, run: markFormer } = useAsyncAction(async () => {
    failure.value = null
    await stationMembers.markFormer(memberId.value)
    formerSuccess.value = true
  })

  const { running: deletingMember, failure: deleteFailure, run: deleteMember } = useAsyncAction(async () => {
    failure.value = null
    await stationMembers.deleteMember(memberId.value)
    deleteSuccess.value = true
  })

  return {
    formerSuccess,
    deleteSuccess,
    formerBlockReasons,
    canMarkFormer,
    markingFormer,
    formerFailure,
    markFormer,
    deletingMember,
    deleteFailure,
    deleteMember,
  }
}
