/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import type { ProfileField } from '@/api/profileFields'
import { StationUserType } from '@/api/types'

function getScopeForUserType(ut: string): string {
  if (ut === StationUserType.MEMBER || ut === StationUserType.TRIAL) return 'MEMBER'
  if (ut === StationUserType.GUARDIAN) return 'GUARDIAN'
  if (ut === StationUserType.TEAM) return 'TEAM'
  if (ut === StationUserType.MANAGER) return 'MANAGER'
  return 'MEMBER'
}

/**
 * Owns the profile field catalogue and the stored values of the viewed member,
 * including which fields apply to a given user type.
 */
export function useMemberProfileFields(memberUserType: Ref<string>) {
  const fields = ref<ProfileField[]>([])
  const values = ref<Map<number, string>>(new Map())

  function fieldsForUserType(userType: string): ProfileField[] {
    const scope = getScopeForUserType(userType)
    return fields.value.filter(f => f.scope === scope)
  }

  const applicableFields = computed(() => fieldsForUserType(memberUserType.value))

  function getFieldValue(fieldId: number): unknown {
    const raw = values.value.get(fieldId) ?? ''
    try { return JSON.parse(raw) } catch { return raw }
  }

  function setValues(entries: { fieldId: number; value?: string | null }[]) {
    const map = new Map<number, string>()
    for (const v of entries) { map.set(v.fieldId, v.value ?? '') }
    values.value = map
  }

  return {
    fields,
    values,
    applicableFields,
    fieldsForUserType,
    getFieldValue,
    setValues,
  }
}
