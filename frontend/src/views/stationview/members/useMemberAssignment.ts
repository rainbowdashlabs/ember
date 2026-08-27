/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AssignableMember } from '@/composables/useGroupsConfig'
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
  allMembers: Ref<AssignableMember[]>,
  members: Ref<AssignableMember[]>,
  setMembers: (memberIds: number[]) => Promise<AssignableMember[]>,
  error: Ref<string>,
) {
  const { t } = useI18n()

  /** What the reader typed to narrow the list of people they can still add. */
  const search = ref('')

  /** The kind of member they are looking for, or the empty string for every kind. */
  const userType = ref('')

  function matchesSearch(member: AssignableMember): boolean {
    const needle = search.value.trim().toLowerCase()
    if (!needle) return true
    return memberDisplayName(member).toLowerCase().includes(needle)
      || (member.email ?? '').toLowerCase().includes(needle)
  }

  /**
   * Everyone not assigned yet, narrowed to what the reader is looking for.
   *
   * <p>A station with hundreds of members offers hundreds of rows to scroll, and the one being looked
   * for is usually known by name or by what kind of member they are. Both narrow the same list, and
   * neither touches who is already assigned: that list is short by nature.
   */
  const availableMembers = computed(() => {
    const assigned = new Set(members.value.map(m => m.id))
    return allMembers.value
      .filter(m => !assigned.has(m.id))
      .filter(m => !userType.value || m.userType === userType.value)
      .filter(matchesSearch)
      .sort((a, b) => memberDisplayName(a).localeCompare(memberDisplayName(b)))
  })

  /** The kinds actually present among the people who can still be added, so the filter offers no dead ends. */
  const offeredUserTypes = computed(() => {
    const assigned = new Set(members.value.map(m => m.id))
    const kinds = new Set<string>()
    for (const member of allMembers.value) {
      if (!assigned.has(member.id) && member.userType) kinds.add(member.userType)
    }
    return [...kinds].sort()
  })

  async function apply(memberIds: number[]) {
    try {
      members.value = await setMembers(memberIds)
    } catch {
      error.value = t('common.error')
    }
  }

  async function addMember(member: AssignableMember) {
    await apply([...members.value.map(m => m.id), member.id])
  }

  async function removeMember(member: AssignableMember) {
    await apply(members.value.filter(m => m.id !== member.id).map(m => m.id))
  }

  return {availableMembers, offeredUserTypes, search, userType, addMember, removeMember}
}
