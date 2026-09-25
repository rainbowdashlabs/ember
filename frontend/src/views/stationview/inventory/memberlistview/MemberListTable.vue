/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import type { InventoryItem } from '@/api/inventory'
import type { StationMember } from '@/api/types'
import type { DataTableApi } from '@/composables/useDataTable'
import { useInventoryRoutes } from '@/composables/useInventoryRoutes'
import MemberInventoryItems from './MemberInventoryItems.vue'
import { inventoryIdOfColumn, NAME_KEY } from './inventoryMemberColumns'
import type { ItemLabelParts } from './itemLabel'

/**
 * Who holds what, one line per member and one column per inventory. A press on a line opens the
 * member's pieces, or ticks the member while choosing whom to export.
 */
const props = defineProps<{
  table: DataTableApi<StationMember>
  exportMode: boolean
  selectedForExport: Set<number>
  itemsFor: (memberId: number, inventoryId: number) => InventoryItem[]
  parts: ItemLabelParts
}>()

const emit = defineEmits<{
  goToMember: [memberId: number]
  toggleExportSelection: [id: number]
  toggleSelectAll: []
}>()

const { t } = useI18n()
const routes = useInventoryRoutes()

/** Where the member's name leads, or nowhere while the list is being ticked for an export. */
function memberPage(member: StationMember) {
  if (props.exportMode || !routes.member) return null
  return { name: routes.member, params: { memberId: member.id } }
}

/** The inventory columns that show, each with the inventory it stands for. */
const inventoryColumns = computed(() => props.table.visibleColumns
  .map(column => ({ key: column.key, inventoryId: inventoryIdOfColumn(column.key) }))
  .filter((column): column is { key: string, inventoryId: number } => column.inventoryId !== null))

const allSelected = computed(() =>
  props.table.rows.length > 0 && props.selectedForExport.size === props.table.rows.length)

function handleRowClick(member: StationMember) {
  if (props.exportMode) emit('toggleExportSelection', member.id)
  else emit('goToMember', member.id)
}
</script>

<template>
  <RecordTable :table="table" clickable test-id="inventory-members-table" @row-click="handleRowClick">
    <template v-if="exportMode" #lead-head>
      <CheckboxInput :model-value="allSelected" @update:model-value="emit('toggleSelectAll')" />
    </template>
    <template v-if="exportMode" #lead="{row}">
      <span @click.stop>
        <CheckboxInput :model-value="selectedForExport.has(row.id)" @update:model-value="emit('toggleExportSelection', row.id)" />
      </span>
    </template>
    <template #[`cell-${NAME_KEY}`]="{row, text}">
      <RowLink :to="memberPage(row)">
        <span class="flex items-center gap-2 font-medium text-primary">
          <UserAvatar :identity="row.identity" :name="text" size="sm" />
          {{ text }}
        </span>
      </RowLink>
    </template>
    <template v-for="column in inventoryColumns" :key="column.key" #[`cell-${column.key}`]="{row}">
      <MemberInventoryItems :items="itemsFor(row.id, column.inventoryId)" :parts="parts" />
    </template>
    <template #empty>
      <EmptyState>{{ t('inventoryMembers.empty') }}</EmptyState>
    </template>
  </RecordTable>
</template>
