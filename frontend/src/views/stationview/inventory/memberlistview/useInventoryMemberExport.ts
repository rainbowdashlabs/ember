/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import client from '@/api/client'
import { profileFields } from '@/api'
import type { ProfileField } from '@/api/profileFields'
import type { Inventory, InventoryItem } from '@/api/inventory'
import type { StationMember } from '@/api/types'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { saveBlob } from '@/util/downloadAuthed'

/**
 * Options describing how an item should be labelled in the export, mirroring what the table
 * itself is currently showing.
 */
export interface ItemLabelOptions {
  showName: Ref<boolean>
  showInternalId: Ref<boolean>
  showSize: Ref<boolean>
}

/**
 * Exporting the member/inventory matrix as CSV or PDF.
 *
 * Entering export mode preselects everyone currently visible, so the common case — "export what I
 * am looking at" — needs no selection at all. The CSV is built in the browser from the same
 * labels the table shows; the PDF is rendered by the server, which is why it takes the display
 * options rather than the finished text.
 *
 * @param filteredMembers      the members currently visible, used as the initial selection
 * @param displayedInventories the inventory columns currently shown
 * @param visibleInventoryIds  the same columns as ids, for the server-rendered export
 * @param labelOptions         which parts of an item name to include
 * @param memberDisplayName    renders a member's name for the first column
 * @param itemsFor             the items one member holds in one inventory
 * @param formatItemLabel      renders one item exactly as the table does
 */
export function useInventoryMemberExport(
  filteredMembers: Ref<StationMember[]>,
  displayedInventories: Ref<Inventory[]>,
  visibleInventoryIds: Ref<Set<number>>,
  labelOptions: ItemLabelOptions,
  memberDisplayName: (m: StationMember) => string,
  itemsFor: (memberId: number, inventoryId: number) => InventoryItem[],
  formatItemLabel: (item: InventoryItem) => string,
) {
  const { t } = useI18n()

  const exportMode = ref(false)
  const selectedMemberIds = ref<Set<number>>(new Set())
  const selectedFieldIds = ref<Set<number>>(new Set())
  const allFields = ref<ProfileField[]>([])

  async function enter() {
    exportMode.value = true
    selectedMemberIds.value = new Set(filteredMembers.value.map(m => m.id))
    selectedFieldIds.value = new Set()
    try {
      allFields.value = await profileFields.listFields()
    } catch {
      allFields.value = []
    }
  }

  function cancel() {
    exportMode.value = false
    selectedMemberIds.value = new Set()
    selectedFieldIds.value = new Set()
  }

  function toggleField(fieldId: number) {
    const next = new Set(selectedFieldIds.value)
    if (next.has(fieldId)) next.delete(fieldId)
    else next.add(fieldId)
    selectedFieldIds.value = next
  }

  function toggleMember(id: number) {
    const next = new Set(selectedMemberIds.value)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    selectedMemberIds.value = next
  }

  function toggleSelectAll() {
    selectedMemberIds.value = selectedMemberIds.value.size === filteredMembers.value.length
      ? new Set()
      : new Set(filteredMembers.value.map(m => m.id))
  }

  /**
   * Builds the CSV in the browser. Written with a byte-order mark and semicolons because the
   * spreadsheet software in this locale needs both to open it as a table.
   */
  function exportCsv() {
    const selected = filteredMembers.value.filter(m => selectedMemberIds.value.has(m.id))
    const headers = [t('membersList.colName'), ...displayedInventories.value.map(inv => inv.name ?? '')]

    const rows = selected.map(member => [
      memberDisplayName(member),
      ...displayedInventories.value.map(inv => itemsFor(member.id, inv.id)
        .map(item => {
          const label = formatItemLabel(item)
          return item.lostAt ? `${label} (${t('inventoryMembers.lost')})` : label
        })
        .join(', ')),
    ])

    const csv = [headers, ...rows]
      .map(row => row.map(cell => `"${cell.replace(/"/g, '""')}"`).join(';'))
      .join('\n')
    saveBlob(new Blob(['﻿' + csv], {type: 'text/csv;charset=utf-8'}), 'inventory-members.csv')
    exportMode.value = false
  }

  const {running: exporting, error: exportError, run: exportPdf} = useAsyncAction(async () => {
    const res = await client.post('/inventories/members/export', {
      memberIds: [...selectedMemberIds.value],
      inventoryIds: [...visibleInventoryIds.value],
      extraFieldIds: [...selectedFieldIds.value],
      showName: labelOptions.showName.value,
      showInternalId: labelOptions.showInternalId.value,
      showSize: labelOptions.showSize.value,
    }, {responseType: 'blob'})
    saveBlob(res.data as Blob, 'inventory-members.pdf')
    exportMode.value = false
  }, {formatError: () => t('common.error')})

  return {
    exportMode,
    selectedMemberIds,
    selectedFieldIds,
    allFields,
    exporting,
    exportError,
    enter,
    cancel,
    toggleField,
    toggleMember,
    toggleSelectAll,
    exportCsv,
    exportPdf,
  }
}
