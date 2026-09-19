/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ExportFieldPicker from '@/components/export/ExportFieldPicker.vue'
import MemberListTable from './MemberListTable.vue'
import type { InventoryItem } from '@/api/inventory'
import type { ProfileField } from '@/api/profileFields'
import type { StationMember } from '@/api/types'
import type { DataTableApi } from '@/composables/useDataTable'
import type { ExportFieldOption } from '@/composables/useExport'
import type { ItemLabelParts } from './itemLabel'

const { t } = useI18n()

const props = defineProps<{
  table: DataTableApi<StationMember>
  parts: ItemLabelParts
  exportMode: boolean
  allFields: ProfileField[]
  selectedExportFields: Set<number>
  selectedForExport: Set<number>
  itemsFor: (memberId: number, inventoryId: number) => InventoryItem[]
}>()

const emit = defineEmits<{
  (e: 'toggle-export-field', fieldId: number): void
  (e: 'go-to-member', memberId: number): void
  (e: 'toggle-export-selection', memberId: number): void
  (e: 'toggle-select-all'): void
}>()

const fieldOptions = computed((): ExportFieldOption<number>[] =>
  props.allFields.map(f => ({ key: f.id, label: f.name ?? '' })),
)
</script>

<template>
  <ExportFieldPicker
    v-if="exportMode && fieldOptions.length > 0"
    boxed
    layout="inline"
    :label="t('inventoryMembers.exportFieldsHint')"
    :options="fieldOptions"
    :selected="selectedExportFields"
    @toggle="emit('toggle-export-field', $event)"
  />

  <MemberListTable
    :table="table"
    :export-mode="exportMode"
    :selected-for-export="selectedForExport"
    :items-for="itemsFor"
    :parts="parts"
    @go-to-member="emit('go-to-member', $event)"
    @toggle-export-selection="emit('toggle-export-selection', $event)"
    @toggle-select-all="emit('toggle-select-all')"
  />

  <p v-if="table.rows.length > 0" class="text-xs text-(--text-muted)">
    {{ table.rows.length }} {{ t('inventoryMembers.memberCount') }}
  </p>
</template>
