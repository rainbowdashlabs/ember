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
import type { InventoryItem, MemberWithName, ProfileField } from '@/api/generated/schema'
import type { DataTableApi } from '@/composables/useDataTable'
import type { ExportFieldOption } from '@/composables/useExport'
import type { ItemLabelParts } from './itemLabel'

const { t } = useI18n()

const props = defineProps<{
  table: DataTableApi<MemberWithName>
  parts: ItemLabelParts
  exportMode: boolean
  allFields: ProfileField[]
  selectedExportFields: Set<number>
  selectedForExport: Set<number>
  itemsFor: (memberId: number, inventoryId: number) => InventoryItem[]
}>()

const emit = defineEmits<{
  'toggle-export-field': [fieldId: number]
  'go-to-member': [memberId: number]
  'toggle-export-selection': [memberId: number]
  'toggle-select-all': []
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
