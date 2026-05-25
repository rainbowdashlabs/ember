/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type Ref, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {attendance} from '@/api'
import type {AttendanceEntry, AttendanceTemplateField} from '@/api/types'

export function useSessionFields(
    sessionId: Ref<number>,
    templateFields: Ref<AttendanceTemplateField[]>,
    entries: Ref<AttendanceEntry[]>,
    error: Ref<string>,
) {
  const {t} = useI18n()
  const fieldValues = ref<Map<number, string>>(new Map())

  function getFieldValue(fieldId: number): string {
    return fieldValues.value.get(fieldId) ?? ''
  }

  function setFieldValue(fieldId: number, val: string) {
    fieldValues.value = new Map([...fieldValues.value, [fieldId, val]])
  }

  function parseFieldConfig(configStr?: string): { options?: string[]; groupId?: number; autoAttend?: boolean } {
    if (!configStr) return {}
    try {
      return JSON.parse(configStr)
    } catch {
      return {}
    }
  }

  async function saveField(fieldId: number) {
    try {
      await attendance.setSessionFields(sessionId.value, {
        fields: [{fieldId, value: JSON.stringify(getFieldValue(fieldId))}],
      })
    } catch {
      error.value = t('common.error')
    }
  }

  const fieldSaveTimers = new Map<number, ReturnType<typeof setTimeout>>()

  function onFieldUpdate(fieldId: number, value: string, immediate: boolean) {
    setFieldValue(fieldId, value)
    const existing = fieldSaveTimers.get(fieldId)
    if (existing) clearTimeout(existing)
    if (immediate) {
      saveField(fieldId)
    } else {
      fieldSaveTimers.set(fieldId, setTimeout(() => saveField(fieldId), 500))
    }
  }

  async function setFieldMemberIds(fieldId: number, ids: string[]) {
    const val = ids.length === 0 ? '' : ids.length === 1 ? ids[0] : JSON.stringify(ids)
    setFieldValue(fieldId, val)
    await saveField(fieldId)

    const field = templateFields.value.find(f => f.id === fieldId)
    if (field && parseFieldConfig(field.config).autoAttend) {
      const entryMemberIds = new Set(entries.value.map(e => e.memberId))
      for (const id of ids) {
        const mid = Number(id)
        if (!entryMemberIds.has(mid)) {
          entries.value = await attendance.createEntry(sessionId.value, {memberId: mid, source: 'EXTRA'})
          entryMemberIds.add(mid)
        }
        const entry = entries.value.find(e => e.memberId === mid)
        if (entry && entry.status !== 'PRESENT') {
          await attendance.updateEntryStatus(entry.id, 'PRESENT')
        }
      }
      const detail = await attendance.getSession(sessionId.value)
      entries.value = detail.entries ?? []
    }
  }

  function initFieldValues(sessionFields: { fieldId: number; value?: string }[]) {
    const fv = new Map<number, string>()
    for (const sf of sessionFields) {
      let val = sf.value ?? ''
      try {
        val = JSON.parse(val)
      } catch { /* use as-is */ }
      fv.set(sf.fieldId, typeof val === 'string' ? val : String(val))
    }
    fieldValues.value = fv
  }

  return {
    fieldValues,
    parseFieldConfig,
    onFieldUpdate,
    setFieldMemberIds,
    initFieldValues,
  }
}
