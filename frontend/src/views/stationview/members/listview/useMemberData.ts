/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, computed } from 'vue'
import type { ProfileField } from '@/api/profileFields'
import type { StationMember, MemberGroup, UserTag, PermissionGrant } from '@/api/types'
import { parseFieldConfig } from '@/api/profileFields'
import { profileFields, stationMembers } from '@/api'
import { useAsyncLoader } from '@/composables/useAsyncLoader'

export function computeAge(dateStr: string, mode: string): string {
  if (!dateStr) return ''
  const birth = new Date(dateStr)
  const now = new Date()
  const target = mode === 'end_of_year' ? new Date(now.getFullYear(), 11, 31) : now
  let age = target.getFullYear() - birth.getFullYear()
  const m = target.getMonth() - birth.getMonth()
  if (m < 0 || (m === 0 && target.getDate() < birth.getDate())) age--
  return String(age)
}

export function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

export function getMemberFirstName(m: StationMember): string {
  const name = m.name ?? ''
  return name.split(' ')[0] ?? ''
}

export function getMemberLastName(m: StationMember): string {
  const name = m.name ?? ''
  return name.split(' ').slice(1).join(' ') ?? ''
}

export function useMemberData() {
  const members = ref<StationMember[]>([])
  const fields = ref<ProfileField[]>([])
  const allGroups = ref<MemberGroup[]>([])
  const allTags = ref<UserTag[]>([])
  const allRoles = ref<PermissionGrant[]>([])
  const memberValues = ref<Map<number, Map<number, string>>>(new Map())
  const memberRolesMap = ref<Map<number, string[]>>(new Map())
  const memberGroupsMap = ref<Map<number, string[]>>(new Map())
  const memberTagsMap = ref<Map<number, string[]>>(new Map())
  const memberManagers = ref<Map<number, StationMember[]>>(new Map())
  const expandedId = ref<number | null>(null)

  const overviewFields = computed(() => fields.value.filter(f => parseFieldConfig(f.config).overview))

  function getRawFieldValue(memberId: number, fieldId: number): unknown {
    const vals = memberValues.value.get(memberId)
    if (!vals) return ''
    const raw = vals.get(fieldId) ?? ''
    try { return JSON.parse(raw) } catch { return raw }
  }

  function getFieldValue(memberId: number, fieldId: number): unknown {
    const field = fields.value.find(f => f.id === fieldId)
    if (field?.fieldType === 'AGE') {
      const cfg = parseFieldConfig(field.config)
      const sourceField = fields.value.find(f => f.name === cfg.sourceField)
      if (sourceField) {
        const dateVal = String(getRawFieldValue(memberId, sourceField.id))
        return computeAge(dateVal, (cfg.ageMode as string) ?? 'now')
      }
      return ''
    }
    return getRawFieldValue(memberId, fieldId)
  }

  function getFieldValueAsString(memberId: number, fieldId: number): string {
    const val = getFieldValue(memberId, fieldId)
    if (val == null) return ''
    return String(val)
  }

  function getMemberType(memberId: number): string | null {
    const member = members.value.find(m => m.id === memberId)
    return member?.userType ?? null
  }

  function getMemberGroups(memberId: number): string[] {
    return memberGroupsMap.value.get(memberId) ?? []
  }

  function getMemberTags(memberId: number): string[] {
    return memberTagsMap.value.get(memberId) ?? []
  }

  function getColumnValues(m: StationMember, key: 'name' | 'groups' | 'tags' | number): string[] {
    if (key === 'name') return [memberDisplayName(m)]
    if (key === 'groups') return getMemberGroups(m.id)
    if (key === 'tags') return getMemberTags(m.id)
    const v = getFieldValueAsString(m.id, key)
    return v ? [v] : []
  }

  const {loading, error, reload} = useAsyncLoader(async () => {
    const [richMembers, allFields, roles] = await Promise.all([
      stationMembers.listRichMembers(),
      profileFields.listFields(),
      stationMembers.listAllPermissions(),
    ])
    fields.value = allFields
    allRoles.value = roles

    const memberList: StationMember[] = []
    const valMap = new Map<number, Map<number, string>>()
    const rolesMap = new Map<number, string[]>()
    const groupsMap = new Map<number, string[]>()
    const tagsMap = new Map<number, string[]>()
    const groupSet = new Map<number, MemberGroup>()
    const tagSet = new Map<number, UserTag>()

    for (const rm of richMembers) {
      memberList.push({
        id: rm.id,
        stationId: String(rm.stationId),
        accountId: rm.accountId ?? 0,
        name: rm.name,
        email: rm.email,
        userType: rm.userType,
        identity: rm.identity,
        accountSetupPending: rm.accountSetupPending,
      })

      rolesMap.set(rm.id, rm.roles)

      const fieldMap = new Map<number, string>()
      for (const [key, val] of Object.entries(rm.profileValues)) {
        fieldMap.set(Number(key), val != null ? String(val) : '')
      }
      valMap.set(rm.id, fieldMap)

      groupsMap.set(rm.id, rm.groups.map(g => g.name))
      for (const g of rm.groups) {
        if (!groupSet.has(g.id)) {
          groupSet.set(g.id, { id: g.id, stationId: String(rm.stationId), name: g.name })
        }
      }

      tagsMap.set(rm.id, rm.tags.map(t => t.name))
      for (const tag of rm.tags) {
        if (!tagSet.has(tag.id)) {
          tagSet.set(tag.id, { id: tag.id, stationId: String(rm.stationId), name: tag.name })
        }
      }
    }

    members.value = memberList
    memberValues.value = valMap
    memberRolesMap.value = rolesMap
    memberGroupsMap.value = groupsMap
    memberTagsMap.value = tagsMap
    allGroups.value = Array.from(groupSet.values())
    allTags.value = Array.from(tagSet.values())
  })

  async function toggleExpand(member: StationMember) {
    if (expandedId.value === member.id) { expandedId.value = null; return }
    expandedId.value = member.id
    if (!memberManagers.value.has(member.id)) {
      try {
        const managers = await stationMembers.getManagers(member.id)
        memberManagers.value = new Map([...memberManagers.value, [member.id, managers]])
      } catch { /* ignore */ }
    }
  }

  return {
    members,
    fields,
    allGroups,
    allTags,
    allRoles,
    memberValues,
    memberRolesMap,
    memberGroupsMap,
    memberTagsMap,
    memberManagers,
    loading,
    error,
    expandedId,
    overviewFields,
    getFieldValue,
    getFieldValueAsString,
    getMemberType,
    getMemberGroups,
    getMemberTags,
    getColumnValues,
    reload,
    toggleExpand,
  }
}
