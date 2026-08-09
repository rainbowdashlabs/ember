/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ProfileField } from '@/api/profileFields'
import type { StationMember } from '@/api/types'
import { members, profileFields, stationMembers } from '@/api'
import { memberDisplayName } from '../listview/useMemberData'

/**
 * Owns the managers linked to the viewed member: their profile snapshots plus
 * linking an existing member, unlinking one and inviting a brand new manager.
 */
export function useMemberManagers(
    memberId: Ref<number>,
    allMembers: Ref<StationMember[]>,
    fieldsForUserType: (userType: string) => ProfileField[],
    error: Ref<string>,
) {
  const { t } = useI18n()

  const managers = ref<StationMember[]>([])
  const managerValues = ref<Map<number, Map<number, string>>>(new Map())
  const managerUserTypes = ref<Map<number, string>>(new Map())

  const managerUserTypesAsRoleMap = computed(() => {
    const result = new Map<number, string[]>()
    for (const [id, ut] of managerUserTypes.value) {
      result.set(id, ut ? [ut] : [])
    }
    return result
  })

  const availableManagers = computed(() => {
    const managerIds = new Set(managers.value.map(m => m.id))
    managerIds.add(memberId.value)
    return allMembers.value
        .filter(m => !managerIds.has(m.id))
        .sort((a, b) => memberDisplayName(a).localeCompare(memberDisplayName(b)))
  })

  function getManagerFields(mgrId: number): ProfileField[] {
    return fieldsForUserType(managerUserTypes.value.get(mgrId) ?? '')
  }

  function getManagerFieldValue(mgrId: number, fieldId: number): unknown {
    const vals = managerValues.value.get(mgrId)
    if (!vals) return ''
    const raw = vals.get(fieldId) ?? ''
    try { return JSON.parse(raw) } catch { return raw }
  }

  async function loadDetails(mgrs: StationMember[]) {
    const mgrVals = new Map<number, Map<number, string>>()
    const mgrTypes = new Map<number, string>()
    for (const mgr of mgrs) {
      try {
        const [vals, memberData] = await Promise.all([
          profileFields.getValues(mgr.id),
          stationMembers.getMember(mgr.id),
        ])
        const fieldMap = new Map<number, string>()
        for (const v of vals) { fieldMap.set(v.fieldId, v.value ?? '') }
        mgrVals.set(mgr.id, fieldMap)
        mgrTypes.set(mgr.id, memberData.userType ?? '')
      } catch { void 0 }
    }
    managerValues.value = mgrVals
    managerUserTypes.value = mgrTypes
  }

  async function linkManager(managerId: number) {
    error.value = ''
    try {
      const currentIds = managers.value.map(m => m.id)
      await stationMembers.setManagers(memberId.value, { managerIds: [...currentIds, managerId] })
      managers.value = await stationMembers.getManagers(memberId.value)
      await loadDetails(managers.value)
    } catch { error.value = t('common.error') }
  }

  async function removeManager(mgrId: number) {
    error.value = ''
    try {
      const newIds = managers.value.filter(m => m.id !== mgrId).map(m => m.id)
      await stationMembers.setManagers(memberId.value, { managerIds: newIds })
      managers.value = await stationMembers.getManagers(memberId.value)
    } catch { error.value = t('common.error') }
  }

  async function createManager(data: { firstName: string; lastName: string; email: string }) {
    error.value = ''
    try {
      const invited = await members.invite({ email: data.email, firstName: data.firstName, lastName: data.lastName })
      const updatedMembers = await stationMembers.listMembers()
      const newMember = updatedMembers.find(m => m.accountId === invited.id)
      if (newMember) {
        const currentIds = managers.value.map(m => m.id)
        await stationMembers.setManagers(memberId.value, { managerIds: [...currentIds, newMember.id] })
        managers.value = await stationMembers.getManagers(memberId.value)
        await loadDetails(managers.value)
        allMembers.value = updatedMembers
      }
    } catch { error.value = t('common.error') }
  }

  return {
    managers,
    managerValues,
    managerUserTypesAsRoleMap,
    availableManagers,
    getManagerFields,
    getManagerFieldValue,
    loadDetails,
    linkManager,
    removeManager,
    createManager,
  }
}
