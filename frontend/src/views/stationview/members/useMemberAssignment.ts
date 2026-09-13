/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { fromMember, userTypesOf } from '@/components/input/select/memberOption'
import type { AssignableMember } from '@/composables/useGroupsConfig'

/**
 * Assigning members to a group or a tag.
 *
 * Both endpoints take the complete membership rather than a delta, so adding one member means
 * sending the existing ones with it. The menu offers everyone not already assigned.
 *
 * @param allMembers the station's members
 * @param members    the members currently assigned, replaced by every change
 * @param setMembers writes the new membership and returns what was stored
 * @param error      the view's error channel
 */
export function useMemberAssignment(
  allMembers: Ref<AssignableMember[]>,
  members: Ref<AssignableMember[]>,
  setMembers: (memberIds: number[]) => Promise<AssignableMember[]>,
  error: Ref<string>,
) {
  const { t } = useI18n()

  /**
   * Everyone not assigned yet, as the menu offers them.
   *
   * <p>Narrowing them is the menu's own business: it searches, it filters by kind and it orders, the
   * same way in every place the product asks which member. Who is already assigned is not narrowed at
   * all, because that list is short by nature.
   */
  const availableMembers = computed(() => {
    const assigned = new Set(members.value.map(m => m.id))
    return allMembers.value.filter(m => !assigned.has(m.id)).map(fromMember)
  })

  /** The kinds actually present among the people who can still be added, so the filter offers no dead ends. */
  const offeredUserTypes = computed(() => userTypesOf(availableMembers.value).toSorted())

  async function apply(memberIds: number[]) {
    try {
      members.value = await setMembers(memberIds)
    } catch {
      error.value = t('common.error')
    }
  }

  async function addMember(memberId: number) {
    await apply([...members.value.map(m => m.id), memberId])
  }

  async function removeMember(memberId: number) {
    await apply(members.value.filter(m => m.id !== memberId).map(m => m.id))
  }

  return {availableMembers, offeredUserTypes, addMember, removeMember}
}
