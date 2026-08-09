/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type { InventoryItem } from '@/api/inventory'
import type { CheckResult, MemberCheckState, RequiredInventoryItem } from '@/api/inventoryCheck'
import CheckMemberHeader from './CheckMemberHeader.vue'
import CheckMemberSubmitBar from './CheckMemberSubmitBar.vue'
import RapidCheckMode from './RapidCheckMode.vue'
import type { CheckEntry } from '@/composables/useMemberCheck'
import InventorySection from './InventorySection.vue'
import { formatDateTime } from '@/util/format'

defineProps<{
  state: MemberCheckState
  checkMode: boolean
  uncheckedEntries: CheckEntry[]
  allMarked: boolean
  submitting: boolean
  itemResults: Map<number, CheckResult>
  itemNotes: Map<number, string>
  procurementCreated: Set<number>
  slotsNotInPossession: Set<string>
  slotSelections: Map<string, string>
  assignedForInventory: (inventoryId: number) => InventoryItem[]
  availableForInventory: (inventoryId: number) => InventoryItem[]
  emptySlotCount: (req: RequiredInventoryItem) => number
  sizeLabel: (req: RequiredInventoryItem, sizeId?: number | null) => string
  itemLabel: (item: InventoryItem, req: RequiredInventoryItem) => string
}>()

defineEmits<{
  startCheckMode: []
  markAllConfirmed: []
  cancel: []
  submit: []
  rapidSetResult: [result: CheckResult]
  rapidMarkNotInPossession: []
  rapidAssign: [itemIdStr: string]
  rapidCreateAndAssign: [sizeIdStr: string]
  rapidDone: []
  setResult: [itemId: number, result: CheckResult]
  setNote: [itemId: number, note: string]
  unassign: [itemId: number]
  createProcurement: [item: InventoryItem]
  changeItem: [currentItemId: number]
  createAndChange: [currentItemId: number, req: RequiredInventoryItem]
  toggleNotInPossession: [inventoryId: number, slotIndex: number]
  assignToSlot: [inventoryId: number, slotIndex: number]
  createAndAssignToSlot: [req: RequiredInventoryItem, slotIndex: number]
  updateSelection: [key: string, value: string]
}>()

const { t } = useI18n()

const rapidCheckRef = ref<InstanceType<typeof RapidCheckMode> | null>(null)

/** Returns the rapid-check entry currently focused, or null when not in rapid mode. */
function getCurrentRapidEntry(): CheckEntry | null {
  return rapidCheckRef.value?.currentEntry ?? null
}

defineExpose({ getCurrentRapidEntry })
</script>

<template>
  <CheckMemberHeader
    :state="state"
    :unchecked-count="uncheckedEntries.length"
    :check-mode="checkMode"
    @start-check-mode="$emit('startCheckMode')"
    @mark-all-confirmed="$emit('markAllConfirmed')"
    @cancel="$emit('cancel')"
  />
  <NeutralContainer v-if="state.lastCheck">
    <div class="text-sm text-(--text-muted)">
      {{ t('inventory.check.lastChecked') }}: {{ formatDateTime(state.lastCheck.checkedAt) }}
    </div>
  </NeutralContainer>
  <RapidCheckMode
    v-if="checkMode"
    ref="rapidCheckRef"
    :unchecked-entries="uncheckedEntries"
    :available-for-inventory="availableForInventory"
    :item-label="itemLabel"
    :size-label="sizeLabel"
    @set-result="(r) => $emit('rapidSetResult', r)"
    @mark-not-in-possession="$emit('rapidMarkNotInPossession')"
    @assign="(id) => $emit('rapidAssign', id)"
    @create-and-assign="(id) => $emit('rapidCreateAndAssign', id)"
    @skip="() => {}"
    @done="$emit('rapidDone')"
  />
  <div v-if="!checkMode" class="space-y-6">
    <InventorySection
      v-for="req in state.required"
      :key="req.inventoryId"
      :req="req"
      :assigned-items="assignedForInventory(req.inventoryId)"
      :available-items="availableForInventory(req.inventoryId)"
      :empty-slot-count="emptySlotCount(req)"
      :item-results="itemResults"
      :item-notes="itemNotes"
      :procurement-created="procurementCreated"
      :slots-not-in-possession="slotsNotInPossession"
      :slot-selections="slotSelections"
      :size-label="sizeLabel"
      :item-label="itemLabel"
      @set-result="(id, r) => $emit('setResult', id, r)"
      @set-note="(id, n) => $emit('setNote', id, n)"
      @unassign="(id) => $emit('unassign', id)"
      @create-procurement="(item) => $emit('createProcurement', item)"
      @change-item="id => $emit('changeItem', id)"
      @create-and-change="(id, r) => $emit('createAndChange', id, r)"
      @toggle-not-in-possession="(invId, slotIdx) => $emit('toggleNotInPossession', invId, slotIdx)"
      @assign-to-slot="(invId, slotIdx) => $emit('assignToSlot', invId, slotIdx)"
      @create-and-assign-to-slot="(r, slotIdx) => $emit('createAndAssignToSlot', r, slotIdx)"
      @update-selection="(key, val) => $emit('updateSelection', key, val)"
    />
  </div>
  <CheckMemberSubmitBar
    :all-marked="allMarked"
    :submitting="submitting"
    @cancel="$emit('cancel')"
    @submit="$emit('submit')"
  />
</template>
