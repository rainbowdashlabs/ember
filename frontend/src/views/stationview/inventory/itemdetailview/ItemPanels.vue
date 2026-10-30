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
import {ItemOwner} from '@/api/generated/schema'
import type {
  HistoryResponse,
  InventoryItem,
  InventorySize,
  ItemCheckHistoryEntry,
  ItemLocationResponse,
} from '@/api/generated/schema'
import type {MemberLike} from '@/components/input/select/memberOption'
import type {Failure} from '@/util/failure'

/**
 * Everything one piece of gear shows below its heading, in the order it is read: what it is, what a
 * station may do about somebody else's, what may be done to it, and what has happened to it.
 */
const props = defineProps<{
  item: InventoryItem
  itemId: number
  sizes: InventorySize[]
  members: MemberLike[]
  location: ItemLocationResponse | null
  historyEntries: HistoryResponse[]
  checkHistory: ItemCheckHistoryEntry[]
  canEditItem: boolean
  canActOnItem: boolean
  canAssign: boolean
  ownedElsewhere: boolean
  isManager: boolean
}>()

const emit = defineEmits<{
  updated: [item: InventoryItem]
  error: [failure: Failure]
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
      @error="emit('error', $event)"
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
