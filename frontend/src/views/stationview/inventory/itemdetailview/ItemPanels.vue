/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import NoteEditor from '@/components/comment/NoteEditor.vue'
import ItemMetadataPanel from './ItemMetadataPanel.vue'
import ItemActionsPanel from './ItemActionsPanel.vue'
import OwnedElsewherePanel from './OwnedElsewherePanel.vue'
import ReportLossPanel from './ReportLossPanel.vue'
import ItemHistoryPanel from './ItemHistoryPanel.vue'
import ItemCheckHistoryPanel from './ItemCheckHistoryPanel.vue'
import LendingSharePanel from '@/components/lending/LendingSharePanel.vue'
import {ItemOwner, type InventoryItem, type InventoryItemHistory, type InventorySize} from '@/api/inventory'
import type {ItemCheckHistoryEntry, ItemLocationResponse} from '@/api/inventoryContainers'
import type {StationMember} from '@/api/types'

/**
 * Everything one piece of gear shows below its heading, in the order it is read: what it is, what a
 * station may do about somebody else's, what may be done to it, and what has happened to it.
 */
const props = defineProps<{
  item: InventoryItem
  itemId: number
  sizes: InventorySize[]
  members: StationMember[]
  location: ItemLocationResponse | null
  historyEntries: InventoryItemHistory[]
  checkHistory: ItemCheckHistoryEntry[]
  canEditItem: boolean
  canActOnItem: boolean
  canAssign: boolean
  ownedElsewhere: boolean
  isManager: boolean
}>()

const emit = defineEmits<{
  updated: [item: InventoryItem]
  error: []
  reload: []
  assign: []
  unassign: []
  markLost: []
  markFound: []
}>()
</script>

<template>
  <ItemMetadataPanel
      :item="props.item"
      :sizes="props.sizes"
      :members="props.members"
      :location="props.location"
      :can-edit-item="props.canEditItem"
      @updated="emit('updated', $event)"
      @error="emit('error')"
  />

  <OwnedElsewherePanel v-if="props.ownedElsewhere" :item="props.item" @started="emit('reload')"/>

  <ReportLossPanel
      v-if="props.ownedElsewhere && props.isManager && props.item.lostAt"
      :item="props.item"
      @reported="emit('reload')"
  />

  <ItemActionsPanel
      v-if="props.canActOnItem"
      :can-assign="props.canAssign"
      :item="props.item"
      @assign="emit('assign')"
      @unassign="emit('unassign')"
      @mark-lost="emit('markLost')"
      @mark-found="emit('markFound')"
  />

  <LendingSharePanel
      :target-id="props.itemId"
      :target-name="props.item.name ?? ''"
      :lendable="props.item.ownerKind === ItemOwner.STATION"
      target="item"
  />

  <ItemHistoryPanel :entries="props.historyEntries"/>

  <ItemCheckHistoryPanel :entries="props.checkHistory"/>

  <NeutralContainer v-if="props.isManager">
    <NoteEditor :entity-type="'ITEM'" :entity-id="props.itemId"/>
  </NeutralContainer>
</template>
