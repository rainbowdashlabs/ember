/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SubHeader from '@/components/typography/SubHeader.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import MemberInventoryLink from '@/components/inventory/MemberInventoryLink.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'
import type {ProcurementEntry} from '@/api/procurement'
import {useDataTable} from '@/composables/useDataTable'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import InventoryTypeCell from './InventoryTypeCell.vue'
import {INVENTORY_TYPE_KEY, inventoryTypeColumn} from './inventoryTypeColumn'

/**
 * What is still to be bought, as the inventory overview shows it. A press on a row leads to the
 * procurement page, where it is dealt with.
 */
const props = defineProps<{
  entries: ProcurementEntry[]
  inventoryTypeMap: Map<number, string>
}>()

const {t} = useI18n()
const router = useRouter()
const routes = useInventoryRoutes()

function sizeOf(entry: ProcurementEntry): string {
  return entry.sizeLabel || t('common.unisize')
}

const columns = computed<TableColumn<ProcurementEntry>[]>(() => [
  {key: 'item', label: t('inventory.overview.colItem'), type: ColumnTypes.TEXT, value: entry => entry.inventoryName},
  inventoryTypeColumn<ProcurementEntry>(t, entry => props.inventoryTypeMap.get(entry.inventoryId)),
  {key: 'member', label: t('inventory.overview.colOwner'), type: ColumnTypes.TEXT, value: entry => entry.memberName},
  {key: 'notes', label: t('inventory.overview.colNotes'), type: ColumnTypes.TEXT, value: entry => entry.notes},
  {key: 'requested', label: t('inventory.overview.colRequested'), type: ColumnTypes.DATE, value: entry => entry.requestedAt},
])

const table = useDataTable<ProcurementEntry>({
  id: 'inventory-overview-procurement',
  rows: () => props.entries,
  columns,
  rowKey: entry => entry.id,
  searchText: sizeOf,
})

function toProcurement() {
  void router.push({name: routes.procurement})
}
</script>

<template>
  <div class="space-y-3">
    <SubHeader>
      <font-awesome-icon :icon="['fas', 'folder-plus']" class="mr-2"/>
      {{ t('inventory.overview.procurement') }} ({{ props.entries.length }})
    </SubHeader>
    <RecordTable :table="table" clickable test-id="overview-procurement" @row-click="toProcurement">
      <template #cell-item="{row, text}">
        {{ text }}
        <SizeBadge>{{ sizeOf(row) }}</SizeBadge>
      </template>
      <template #[`cell-${INVENTORY_TYPE_KEY}`]="{row, text}">
        <InventoryTypeCell :text="text" :type="props.inventoryTypeMap.get(row.inventoryId)"/>
      </template>
      <template #cell-member="{row}">
        <MemberInventoryLink :identity="row.memberIdentity ?? null" :member-id="row.memberId"/>
      </template>
    </RecordTable>
  </div>
</template>
