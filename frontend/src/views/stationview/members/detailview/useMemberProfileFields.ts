/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import type { ProfileField } from '@/api/profileFields'
import { StationUserType } from '@/api/types'
import { useFieldAudiences } from '@/composables/useFieldAudiences'

/**
 * A trial member is a kind of their own now, so a station can ask them less than it asks a member.
 * Everybody else stands for themselves.
 */
function roleOfUserType(userType: string): string {
  switch (userType) {
    case StationUserType.TRIAL: return 'TRIAL'
    case StationUserType.GUARDIAN: return 'GUARDIAN'
    case StationUserType.TEAM: return 'TEAM'
    case StationUserType.MANAGER: return 'MANAGER'
    default: return 'MEMBER'
  }
}

/**
 * Owns the profile field catalogue and the stored values of the viewed member,
 * including which fields apply to a given user type.
 */
export function useMemberProfileFields(memberUserType: Ref<string>) {
  const fields = ref<ProfileField[]>([])
  const values = ref<Map<number, string>>(new Map())
  const audiences = useFieldAudiences()

  function fieldsForUserType(userType: string): ProfileField[] {
    return audiences.fieldsFor(fields.value, roleOfUserType(userType))
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
    loadAudiences: audiences.load,
  }
}
