/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import type { FieldOriginName, ProfileQuestion } from '@/api/profileFields'
import { StationUserType } from '@/api/types'
import { calculatedAnswer, profileKey } from '@/util/profileFields'
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
 *
 * <p>The catalogue is the station's own, while the answers arrive for every question the member is
 * asked, the association's included. Both owners number their questions apart, so the answers are held
 * under origin and id together and this screen reads the station's: keyed by the id alone, an
 * association's answer overwrote the station's of the same number.
 */
export function useMemberProfileFields(memberUserType: Ref<string>) {
  const fields = ref<ProfileQuestion[]>([])
  const values = ref<Map<string, string>>(new Map())
  const audiences = useFieldAudiences()

  function fieldsForUserType(userType: string): ProfileQuestion[] {
    return audiences.fieldsFor(fields.value, roleOfUserType(userType))
  }

  const applicableFields = computed(() => fieldsForUserType(memberUserType.value))

  function rawValue(fieldId: number): unknown {
    const raw = values.value.get(profileKey(fieldId, 'STATION')) ?? ''
    try { return JSON.parse(raw) } catch { return raw }
  }

  function getFieldValue(fieldId: number): unknown {
    const field = fields.value.find(f => f.id === fieldId)
    const calculated = field && calculatedAnswer(field, fields.value, rawValue)
    return calculated ?? rawValue(fieldId)
  }

  function setValues(entries: { fieldId: number; value?: string | null; origin?: FieldOriginName }[]) {
    const map = new Map<string, string>()
    for (const v of entries) { map.set(profileKey(v.fieldId, v.origin ?? 'STATION'), v.value ?? '') }
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
