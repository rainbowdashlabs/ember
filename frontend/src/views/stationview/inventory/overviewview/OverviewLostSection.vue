/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import MemberInventoryLink from '@/components/inventory/MemberInventoryLink.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'
import {useDataTable} from '@/composables/useDataTable'
import InventoryTypeCell from './InventoryTypeCell.vue'
import {INVENTORY_TYPE_KEY, inventoryTypeColumn} from './inventoryTypeColumn'
import type {LostItem} from './types'

/** The pieces reported lost, as the inventory overview shows them: what, out of which kind, and on whom. */
const props = defineProps<{
  items: LostItem[]
}>()

const {t} = useI18n()

const columns = computed<TableColumn<LostItem>[]>(() => [
  {key: 'item', label: t('inventory.overview.colItem'), type: ColumnTypes.TEXT, value: lost => lost.item.name},
  inventoryTypeColumn<LostItem>(t, lost => lost.inventoryType),
  {key: 'member', label: t('inventory.overview.colOwner'), type: ColumnTypes.TEXT, value: lost => lost.ownerName},
  {key: 'lostAt', label: t('inventory.overview.colLostSince'), type: ColumnTypes.DATE, value: lost => lost.item.lostAt},
])

const table = useDataTable<LostItem>({
  id: 'inventory-overview-lost',
  rows: () => props.items,
  columns,
  rowKey: lost => lost.item.id,
  searchText: lost => `${lost.item.internalId ?? ''} ${lost.sizeName}`,
})
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('inventory.overview.lost') }}</SubHeader>
    <RecordTable :table="table" test-id="overview-lost">
      <template #cell-item="{row, text}">
        <span class="font-medium">{{ text }}</span>
        <SizeBadge lost>{{ row.sizeName || t('common.unisize') }}</SizeBadge>
        <MutedText v-if="row.item.internalId" tag="div">{{ row.item.internalId }}</MutedText>
      </template>
      <template #[`cell-${INVENTORY_TYPE_KEY}`]="{row, text}">
        <InventoryTypeCell :text="text" :type="row.inventoryType"/>
      </template>
      <template #cell-member="{row}">
        <MemberInventoryLink :identity="row.ownerIdentity" :member-id="row.item.assignedTo"/>
      </template>
      <template #cell-lostAt="{text}">
        <ErrorBadge v-if="text">{{ text }}</ErrorBadge>
      </template>
    </RecordTable>
  </div>
</template>
