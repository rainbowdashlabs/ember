/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { StationMember } from '@/api/types'
import { memberDisplayName } from './listview/useMemberData'

/**
 * Assigning members to a group or a tag.
 *
 * Both endpoints take the complete membership rather than a delta, so adding one member means
 * sending the existing ones with it. The picker offers everyone not already assigned, ordered by
 * display name so a station with hundreds of members stays navigable.
 *
 * @param allMembers the station's members
 * @param members    the members currently assigned, replaced by every change
 * @param setMembers writes the new membership and returns what was stored
 * @param error      the view's error channel
 */
export function useMemberAssignment(
  allMembers: Ref<StationMember[]>,
  members: Ref<StationMember[]>,
  setMembers: (memberIds: number[]) => Promise<StationMember[]>,
  error: Ref<string>,
) {
  const { t } = useI18n()

  const availableMembers = computed(() => {
    const assigned = new Set(members.value.map(m => m.id))
    return allMembers.value
      .filter(m => !assigned.has(m.id))
      .sort((a, b) => memberDisplayName(a).localeCompare(memberDisplayName(b)))
  })

  async function apply(memberIds: number[]) {
    try {
      members.value = await setMembers(memberIds)
    } catch {
      error.value = t('common.error')
    }
  }

  async function addMember(member: StationMember) {
    await apply([...members.value.map(m => m.id), member.id])
  }

  async function removeMember(member: StationMember) {
    await apply(members.value.filter(m => m.id !== member.id).map(m => m.id))
  }

  return {availableMembers, addMember, removeMember}
}
