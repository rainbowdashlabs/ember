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
import ItemsByArt from './ItemsByArt.vue'
import type {InventoryArt} from '@/api/inventoryArts'
import type { ItemTableApi } from '../itemtable/useItemTable'
import ItemTableFilterModal from '../itemtable/ItemTableFilterModal.vue'
import ItemListControls from '../itemtable/ItemListControls.vue'
import InventoryStatsPanel from './InventoryStatsPanel.vue'
import LentOutTable from './LentOutTable.vue'
import LendingSharePanel from '@/components/lending/LendingSharePanel.vue'
import ProcurementTable from './ProcurementTable.vue'
import LostItemsTable from './LostItemsTable.vue'
import FreeItemsGrid from './FreeItemsGrid.vue'
import {InventoryTypes, isLendableInventory, type InventoryDetail, type InventoryItem, type InventorySize} from '@/api/inventory'
import type { ProcurementEntry } from '@/api/procurement'
import type { StationMember } from '@/api/types'
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
  /** Whether this screen offers writing a whole inventory down at once, which the association's does not. */
  canTakeStock: boolean
}

withDefaults(defineProps<{
  detail: InventoryDetail
  items: InventoryItem[]
  /** The kinds this drawer has been sorted into. Empty is the ordinary case and the flat list stays. */
  arts?: InventoryArt[]
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
}>(), {arts: () => []})

defineEmits<InventoryItemActionEmits & {
  fulfillProcurement: [id: number]
  openProcurementModal: []
  openQuickAssign: []
  openAdd: []
  openIntake: []
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

  <LendingSharePanel
    :target-id="detail.id"
    :target-name="detail.name ?? ''"
    :lendable="isLendableInventory(detail.inventoryType)"
    target="inventory"
  />

  <LentOutTable :lent-out-items="lentOutItems" :lent-out-count="counts.lentOut" />

  <NeutralContainer v-if="items.length > 0 || permissions.canCreateItem" class="space-y-4">
    <ItemListControls
      :table="itemTable"
      :count="items.length"
      :show-quick-assign="permissions.canCreateItem && permissions.canQuickAssign"
      :show-intake="permissions.canCreateItem && permissions.canTakeStock"
      @intake="$emit('openIntake')"
      :show-add="permissions.canCreateItem && permissions.canAddInternal"
      :show-search="items.length > 0"
      @quick-assign="$emit('openQuickAssign')"
      @add="$emit('openAdd')"
    />
    <ItemsByArt
      v-if="items.length > 0 && arts.length > 0"
      :detail="detail"
      :items="itemTable.filteredItems"
      :arts="arts"
      :member-map="memberMap"
      :lent-out-items="lentOutItems"
      :lent-item-station-map="lentItemStationMap"
      :container-path-by-id="containerPathById"
      :show-actions="permissions.canEdit"
      @assign="$emit('assign', $event)"
      @unassign="$emit('unassign', $event)"
      @edit="$emit('edit', $event)"
      @mark-lost="$emit('markLost', $event)"
      @mark-found="$emit('markFound', $event)"
      @history="$emit('history', $event)"
      @delete="$emit('delete', $event)"
    />
    <ItemsTable
      v-else-if="items.length > 0"
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
