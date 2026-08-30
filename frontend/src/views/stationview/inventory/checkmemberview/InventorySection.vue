/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import InventoryItemCard from './InventoryItemCard.vue'
import EmptySlotCard from './EmptySlotCard.vue'
import type { InventoryItem, RequiredInventoryItem } from '@/api/inventory'
import type { CheckResult } from '@/api/inventoryCheck'

defineProps<{
  req: RequiredInventoryItem
  assignedItems: InventoryItem[]
  availableItems: InventoryItem[]
  emptySlotCount: number
  itemResults: Map<number, CheckResult>
  itemNotes: Map<number, string>
  procurementCreated: Set<number>
  slotsNotInPossession: Set<string>
  slotProcurements: Set<string>
  slotSelections: Map<string, string>
  sizeLabel: (req: RequiredInventoryItem, sizeId?: number | null) => string
  itemLabel: (item: InventoryItem, req: RequiredInventoryItem) => string
}>()

const emit = defineEmits<{
  setResult: [itemId: number, result: CheckResult]
  setNote: [itemId: number, note: string]
  unassign: [itemId: number]
  createProcurement: [item: InventoryItem]
  correct: [item: InventoryItem, req: RequiredInventoryItem]
  toggleNotInPossession: [inventoryId: number, slotIndex: number]
  assignToSlot: [inventoryId: number, slotIndex: number]
  createAndAssignToSlot: [req: RequiredInventoryItem, slotIndex: number]
  createProcurementForSlot: [req: RequiredInventoryItem, slotIndex: number]
  updateSelection: [key: string, value: string]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between gap-2">
      <SubHeader>{{ req.inventoryName }}</SubHeader>
      <MutedText size="sm" class="shrink-0">
        {{ req.assignedQuantity }} / {{ req.requiredQuantity }}
        <span v-if="req.inExchangeQuantity > 0" data-testid="in-exchange">
          {{ t('inventory.check.inExchange', {count: req.inExchangeQuantity}) }}
        </span>
        <span v-if="req.assignedQuantity < req.requiredQuantity" class="text-error">
          ({{ req.requiredQuantity - req.assignedQuantity }} fehlt)
        </span>
      </MutedText>
    </div>

    <!-- Assigned items -->
    <div class="space-y-2">
      <InventoryItemCard
        v-for="item in assignedItems"
        :key="item.id"
        :item="item"
        :req="req"
        :result="itemResults.get(item.id)"
        :note="itemNotes.get(item.id) ?? ''"
        :procurement-created="procurementCreated.has(item.id)"
        :size-label="sizeLabel(req, item.sizeId)"
        @set-result="(id, r) => emit('setResult', id, r)"
        @set-note="(id, n) => emit('setNote', id, n)"
        @unassign="id => emit('unassign', id)"
        @create-procurement="item => emit('createProcurement', item)"
        @correct="(item, r) => emit('correct', item, r)"
      />
    </div>

    <!-- Empty slots -->
    <EmptySlotCard
      v-for="slotIdx in emptySlotCount"
      :key="`empty-${req.inventoryId}-${slotIdx}`"
      :req="req"
      :slot-index="slotIdx"
      :is-not-in-possession="slotsNotInPossession.has(`${req.inventoryId}-${slotIdx}`)"
      :procurement-noted="slotProcurements.has(`${req.inventoryId}-${slotIdx}`)"
      :available-items="availableItems"
      :slot-selections="slotSelections"
      :item-label="itemLabel"
      @toggle-not-in-possession="(inv, si) => emit('toggleNotInPossession', inv, si)"
      @assign-to-slot="(inv, si) => emit('assignToSlot', inv, si)"
      @create-and-assign="(r, si) => emit('createAndAssignToSlot', r, si)"
      @create-procurement="(r, si) => emit('createProcurementForSlot', r, si)"
      @update-selection="(k, v) => emit('updateSelection', k, v)"
    />
  </NeutralContainer>
</template>
