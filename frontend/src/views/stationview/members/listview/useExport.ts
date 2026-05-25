/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref, type ComputedRef } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ProfileField, StationMember } from '@/api/types'
import { getMemberFirstName, getMemberLastName } from './useMemberData'

export function useExport(
  filteredMembers: ComputedRef<StationMember[]>,
  fields: Ref<ProfileField[]>,
  getMemberGroups: (id: number) => string[],
  getFieldValueAsString: (memberId: number, fieldId: number) => string,
) {
  const { t } = useI18n()

  const exportMode = ref(false)
  const selectedIds = ref<Set<number>>(new Set())
  const showExportModal = ref(false)

  function toggleExportMode() {
    exportMode.value = !exportMode.value
    if (!exportMode.value) {
      selectedIds.value = new Set()
    }
  }

  function toggleSelect(memberId: number) {
    const newSet = new Set(selectedIds.value)
    if (newSet.has(memberId)) { newSet.delete(memberId) } else { newSet.add(memberId) }
    selectedIds.value = newSet
  }

  function toggleSelectAll() {
    if (filteredMembers.value.every(m => selectedIds.value.has(m.id))) {
      selectedIds.value = new Set()
    } else {
      selectedIds.value = new Set(filteredMembers.value.map(m => m.id))
    }
  }

  function openExportModal() {
    if (selectedIds.value.size === 0) return
    showExportModal.value = true
  }

  function performExport(columns: string[], format: 'csv' | 'values') {
    const selectedMembers = filteredMembers.value.filter(m => selectedIds.value.has(m.id))

    function getColumnValue(m: StationMember, col: string): string {
      if (col === 'firstName') return getMemberFirstName(m)
      if (col === 'lastName') return getMemberLastName(m)
      if (col === 'email') return m.email ?? ''
      if (col === 'groups') return getMemberGroups(m.id).join(', ')
      if (col.startsWith('field:')) {
        const fieldId = Number(col.slice(6))
        return getFieldValueAsString(m.id, fieldId)
      }
      return ''
    }

    function getColumnLabel(col: string): string {
      if (col === 'firstName') return t('membersList.export.colFirstName')
      if (col === 'lastName') return t('membersList.export.colLastName')
      if (col === 'email') return t('membersList.export.colEmail')
      if (col === 'groups') return t('membersList.export.colGroups')
      if (col.startsWith('field:')) {
        const fieldId = Number(col.slice(6))
        return fields.value.find(f => f.id === fieldId)?.name ?? ''
      }
      return col
    }

    let output: string

    if (format === 'values' && columns.length === 1) {
      const col = columns[0]
      const values = selectedMembers.map(m => getColumnValue(m, col)).filter(v => v)
      output = values.join('; ')
    } else {
      const escapeCsv = (val: string) => {
        if (val.includes(';') || val.includes('"') || val.includes('\n')) {
          return `"${val.replace(/"/g, '""')}"`
        }
        return val
      }
      const header = columns.map(c => escapeCsv(getColumnLabel(c))).join(';')
      const rows = selectedMembers.map(m =>
        columns.map(c => escapeCsv(getColumnValue(m, c))).join(';'),
      )
      output = [header, ...rows].join('\n')
    }

    const blob = new Blob([output], { type: format === 'csv' ? 'text/csv;charset=utf-8' : 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = format === 'csv' ? 'mitglieder.csv' : 'mitglieder.txt'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)

    showExportModal.value = false
    exportMode.value = false
    selectedIds.value = new Set()
  }

  return {
    exportMode,
    selectedIds,
    showExportModal,
    toggleExportMode,
    toggleSelect,
    toggleSelectAll,
    openExportModal,
    performExport,
  }
}
