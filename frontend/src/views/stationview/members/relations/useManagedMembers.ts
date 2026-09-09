/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { StationUserType, type StationMember } from '@/api/types'
import { stationMembers } from '@/api'
import { memberDisplayName } from '../listview/useMemberData'

/**
 * The other half of the guardian relation: the members somebody looks after.
 *
 * <p>It is the same link seen from the other end, and the server stores it once, so linking here
 * and naming a guardian on the member's own page do the same thing. Both ends are offered because
 * either can be the one somebody has open.
 *
 * <p>Nobody is invited from this side. A guardian is created for a member who needs one, which is
 * what the other end offers; a member is not created because their guardian happens to be on
 * screen.
 */
export function useManagedMembers(
    memberId: Ref<number>,
    allMembers: Ref<StationMember[]>,
    error: Ref<string>,
) {
  const { t } = useI18n()

  const managedMembers = ref<StationMember[]>([])

  /**
   * Who may still be taken on. Only the kinds that can have a guardian at all, and nobody who is
   * already looked after by this person, has left the station, or is this person themselves.
   */
  const availableManaged = computed(() => {
    const taken = new Set(managedMembers.value.map(m => m.id))
    taken.add(memberId.value)
    return allMembers.value
        .filter(m => !taken.has(m.id))
        .filter(m => !m.formerAt)
        .filter(m => m.userType === StationUserType.MEMBER || m.userType === StationUserType.TRIAL)
        .sort((a, b) => memberDisplayName(a).localeCompare(memberDisplayName(b)))
  })

  async function write(ids: number[]) {
    error.value = ''
    try {
      managedMembers.value = await stationMembers.setManaged(memberId.value, ids)
    } catch {
      error.value = t('common.error')
    }
  }

  async function linkManaged(id: number) {
    await write([...managedMembers.value.map(m => m.id), id])
  }

  async function removeManaged(id: number) {
    await write(managedMembers.value.filter(m => m.id !== id).map(m => m.id))
  }

  return { managedMembers, availableManaged, linkManaged, removeManaged }
}
