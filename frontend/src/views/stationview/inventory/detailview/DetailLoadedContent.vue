/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ItemsTable from '../ItemsTable.vue'
import type { ItemTableApi } from '../itemtable/useItemTable'
import ItemTableFilterModal from '../itemtable/ItemTableFilterModal.vue'
import ItemListControls from '../itemtable/ItemListControls.vue'
import InventoryStatsPanel from './InventoryStatsPanel.vue'
import LentOutTable from './LentOutTable.vue'
import ProcurementTable from './ProcurementTable.vue'
import LostItemsTable from './LostItemsTable.vue'
import FreeItemsGrid from './FreeItemsGrid.vue'
import type { InventoryDetail, InventoryItem, InventorySize, StationMember, ProcurementEntry } from '@/api/types'
import { InventoryTypes } from '@/api/types'
import type { LentOutItem } from '@/api/lending'
import type { InventoryItemActionEmits } from '../itemEmits'

type SizeStat = {
  size: InventorySize | null
  total: number
  assigned: number
  free: number
  lost: number
  lent: number
}

type Counts = {
  total: number
  lost: number
  lentOut: number
  assigned: number
  free: number
}

type Permissions = {
  canEdit: boolean
  canProcure: boolean
  canCreateItem: boolean
  canQuickAssign: boolean
  canAddInternal: boolean
}

defineProps<{
  detail: InventoryDetail
  items: InventoryItem[]
  freeItems: InventoryItem[]
  lostItems: InventoryItem[]
  memberMap: Map<number, StationMember>
  openProcurement: ProcurementEntry[]
  lentOutItems: LentOutItem[]
  lentItemStationMap: Map<number, string>
  containerPathById: Map<number, string>
  counts: Counts
  allSizeStats: SizeStat[]
  permissions: Permissions
  itemTable: ItemTableApi
}>()

defineEmits<InventoryItemActionEmits & {
  fulfillProcurement: [id: number]
  openProcurementModal: []
  openQuickAssign: []
  openAdd: []
  assignFree: [itemId: number]
}>()

const { t } = useI18n()
</script>

<template>
  <InventoryStatsPanel
    :total-count="counts.total"
    :free-count="counts.free"
    :assigned-count="counts.assigned"
    :lost-count="counts.lost"
    :lent-out-count="counts.lentOut"
    :has-sizes="detail.hasSizes"
    :size-stats="allSizeStats"
  />

  <ProcurementTable
    :entries="openProcurement"
    :readonly="!permissions.canProcure"
    :can-create="permissions.canProcure"
    @fulfill="$emit('fulfillProcurement', $event)"
    @create="$emit('openProcurementModal')"
  />

  <LentOutTable :lent-out-items="lentOutItems" :lent-out-count="counts.lentOut" />

  <NeutralContainer v-if="items.length > 0 || permissions.canCreateItem" class="space-y-4">
    <ItemListControls
      :table="itemTable"
      :count="items.length"
      :show-quick-assign="permissions.canCreateItem && permissions.canQuickAssign"
      :show-add="permissions.canCreateItem && permissions.canAddInternal"
      :show-search="items.length > 0"
      @quick-assign="$emit('openQuickAssign')"
      @add="$emit('openAdd')"
    />
    <ItemsTable
      v-if="items.length > 0"
      :items="items"
      :has-sizes="detail.hasSizes"
      :sizes="detail.sizes"
      :members="memberMap"
      :show-actions="permissions.canEdit"
      :show-history="true"
      :inventory-type="detail.inventoryType ?? InventoryTypes.INTERNAL"
      :lent-out-items="lentOutItems"
      :lent-item-map="lentItemStationMap"
      :container-path-by-id="containerPathById"
      :table-api="itemTable"
      @assign="$emit('assign', $event)"
      @unassign="$emit('unassign', $event)"
      @edit="$emit('edit', $event)"
      @mark-lost="$emit('markLost', $event)"
      @mark-found="$emit('markFound', $event)"
      @history="$emit('history', $event)"
      @delete="$emit('delete', $event)"
    />
  </NeutralContainer>

  <ItemTableFilterModal :table="itemTable"/>

  <FreeItemsGrid
    v-if="permissions.canEdit"
    :items="freeItems"
    :sizes="detail.sizes"
    :container-path-by-id="containerPathById"
    @assign="$emit('assignFree', $event)"
  />

  <div v-if="permissions.canProcure" class="flex gap-2">
    <PrimaryButton :icon="['fas', 'folder-plus']" @click="$emit('openProcurementModal')">
      {{ t('inventory.detail.createProcurement') }}
    </PrimaryButton>
  </div>

  <LostItemsTable :items="lostItems" :sizes="detail.sizes ?? []" :member-map="memberMap" />
</template>
