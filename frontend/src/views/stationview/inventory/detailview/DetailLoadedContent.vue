/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ItemsTable from '../ItemsTable.vue'
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
  counts: Counts
  allSizeStats: SizeStat[]
  permissions: Permissions
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

  <div v-if="items.length > 0 || permissions.canCreateItem" class="flex flex-wrap items-center justify-between gap-2">
    <SubHeader>{{ t('inventory.edit.itemsTitle') }} ({{ items.length }})</SubHeader>
    <div v-if="permissions.canCreateItem" class="flex items-center gap-2">
      <PrimaryButton v-if="permissions.canQuickAssign" :icon="['fas', 'user-plus']" @click="$emit('openQuickAssign')">
        {{ t('inventory.edit.quickAssign') }}
      </PrimaryButton>
      <PrimaryButton v-if="permissions.canAddInternal" :icon="['fas', 'plus']" @click="$emit('openAdd')">
        {{ t('inventory.edit.addItem') }}
      </PrimaryButton>
    </div>
  </div>
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
    @assign="$emit('assign', $event)"
    @unassign="$emit('unassign', $event)"
    @edit="$emit('edit', $event)"
    @mark-lost="$emit('markLost', $event)"
    @mark-found="$emit('markFound', $event)"
    @history="$emit('history', $event)"
    @delete="$emit('delete', $event)"
  />

  <FreeItemsGrid
    v-if="permissions.canEdit"
    :items="freeItems"
    :sizes="detail.sizes"
    @assign="$emit('assignFree', $event)"
  />

  <div v-if="permissions.canProcure" class="flex gap-2">
    <PrimaryButton :icon="['fas', 'folder-plus']" @click="$emit('openProcurementModal')">
      {{ t('inventory.detail.createProcurement') }}
    </PrimaryButton>
  </div>

  <LostItemsTable :items="lostItems" :sizes="detail.sizes ?? []" :member-map="memberMap" />
</template>
