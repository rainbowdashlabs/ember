/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ItemsByArt from './ItemsByArt.vue'
import type {
  InventoryArt,
  InventoryDetail,
  InventoryItem,
  InventorySize,
  LentOutItem,
  ProcurementResponse,
} from '@/api/generated/schema'
import type { ItemTableApi } from '../itemtable/useItemTable'
import InventoryItemTable from '../itemtable/InventoryItemTable.vue'
import ItemListControls from '../itemtable/ItemListControls.vue'
import InventoryStatsPanel from './InventoryStatsPanel.vue'
import LentOutTable from './LentOutTable.vue'
import LendingSharePanel from '@/components/lending/LendingSharePanel.vue'
import ProcurementTable from './ProcurementTable.vue'
import LostItemsTable from './LostItemsTable.vue'
import FreeItemsGrid from './FreeItemsGrid.vue'
import BatchActionBar from './BatchActionBar.vue'
import type { ItemSelection } from '../itemtable/useItemSelection'
import {isLendableInventory} from '@/api/inventory'
import type { MemberLike } from '@/components/input/select/memberOption'
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

const props = withDefaults(defineProps<{
  detail: InventoryDetail
  items: InventoryItem[]
  /** The kinds this drawer has been sorted into. Empty is the ordinary case and the flat list stays. */
  arts?: InventoryArt[]
  freeItems: InventoryItem[]
  lostItems: InventoryItem[]
  memberMap: Map<number, MemberLike>
  openProcurement: ProcurementResponse[]
  lentOutItems: LentOutItem[]
  lentItemStationMap: ReadonlyMap<number, string>
  /** The movement carrying each piece an open movement carries. */
  movementItemMap?: ReadonlyMap<number, number>
  containerPathById: Map<number, string>
  counts: Counts
  allSizeStats: SizeStat[]
  permissions: Permissions
  itemTable: ItemTableApi
  /** The pieces ticked for a change to all of them at once. */
  selection: ItemSelection
}>(), {arts: () => [], movementItemMap: () => new Map()})

defineEmits<InventoryItemActionEmits & {
  fulfillProcurement: [id: number]
  openProcurementModal: []
  openQuickAssign: []
  openAdd: []
  openIntake: []
  assignFree: [itemId: number]
  batchChanged: []
}>()

const { t } = useI18n()

const shownItemIds = computed(() => props.itemTable.table.rows.map(row => row.id))

/** Ticked pieces the search or a filter hides, which a change reaches all the same. */
const hiddenSelectedCount = computed(() => {
  const shown = new Set(shownItemIds.value)
  return props.selection.selectedIds.value.filter(id => !shown.has(id)).length
})

function memberIdentity(memberId: number) {
  return props.memberMap.get(memberId)?.identity
}
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
      :table="itemTable.table"
      :count="items.length"
      :show-quick-assign="permissions.canCreateItem && permissions.canQuickAssign"
      :show-intake="permissions.canCreateItem && permissions.canTakeStock"
      :show-add="permissions.canCreateItem && permissions.canAddInternal"
      :show-search="items.length > 0"
      :show-select="permissions.canEdit && items.length > 0"
      :selecting="selection.active.value"
      @toggle-select="selection.setActive(!selection.active.value)"
      @select-all-shown="selection.selectAll(shownItemIds)"
      @intake="$emit('openIntake')"
      @quick-assign="$emit('openQuickAssign')"
      @add="$emit('openAdd')"
    />
    <ItemsByArt
      v-if="items.length > 0 && arts.length > 0"
      :items="itemTable"
      :arts="arts"
      :member-identity="memberIdentity"
      :lent-item-station-map="lentItemStationMap"
      :movement-item-map="movementItemMap"
      :container-path-by-id="containerPathById"
      :show-actions="permissions.canEdit"
      :selection="selection"
      @assign="$emit('assign', $event)"
      @unassign="$emit('unassign', $event)"
      @edit="$emit('edit', $event)"
      @mark-lost="$emit('markLost', $event)"
      @mark-found="$emit('markFound', $event)"
      @history="$emit('history', $event)"
      @delete="$emit('delete', $event)"
    />
    <InventoryItemTable
      v-else-if="items.length > 0"
      :items="itemTable"
      :member-identity="memberIdentity"
      :show-actions="permissions.canEdit"
      :selection="selection"
      :lent-item-map="lentItemStationMap"
      :movement-item-map="movementItemMap"
      :container-path-by-id="containerPathById"
      @assign="$emit('assign', $event)"
      @unassign="$emit('unassign', $event)"
      @edit="$emit('edit', $event)"
      @mark-lost="$emit('markLost', $event)"
      @mark-found="$emit('markFound', $event)"
      @history="$emit('history', $event)"
      @delete="$emit('delete', $event)"
    />
    <BatchActionBar
      v-if="selection.active.value"
      :inventory-id="detail.id"
      :item-ids="selection.selectedIds.value"
      :arts="arts"
      :offers-art="detail.homogeneous === false"
      :hidden-count="hiddenSelectedCount"
      @changed="$emit('batchChanged')"
      @clear="selection.clear()"
    />
  </NeutralContainer>

  <FreeItemsGrid
    v-if="permissions.canEdit"
    :items="freeItems"
    :sizes="detail.sizes"
    :arts="arts"
    :container-path-by-id="containerPathById"
    :movement-item-map="movementItemMap"
    @assign="$emit('assignFree', $event)"
  />

  <div v-if="permissions.canProcure" class="flex gap-2">
    <PrimaryButton :icon="['fas', 'folder-plus']" @click="$emit('openProcurementModal')">
      {{ t('inventory.detail.createProcurement') }}
    </PrimaryButton>
  </div>

  <LostItemsTable :items="lostItems" :sizes="detail.sizes ?? []" :member-map="memberMap" />
</template>
