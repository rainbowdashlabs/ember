/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, toRef } from 'vue'
import InventorySection from './InventorySection.vue'
import HandOutModal from './inventorytab/HandOutModal.vue'
import ReturnEverythingBar from './inventorytab/ReturnEverythingBar.vue'
import ScanHandOutPanel from './inventorytab/ScanHandOutPanel.vue'
import { useScanHandOut } from './inventorytab/useScanHandOut'
import MovementsPanel from '@/components/inventory/MovementsPanel.vue'
import UnknownScanModal from '@/views/stationview/inventory/UnknownScanModal.vue'
import type { MemberRequirements, MyInventoryItem, RequiredInventoryItem } from '@/api/generated/schema'

/**
 * Everything about the member's gear in one place: a scanner to hand pieces over, what they hold and
 * what they are still owed, and the movements running for them.
 *
 * <p>Each control follows the inventory right it needs, not the right to edit the member's account,
 * because handing out gear is the equipment keeper's work and they need not be able to edit a profile.
 */
const props = defineProps<{
  memberId: number
  memberName: string
  memberInventory: MyInventoryItem[]
  memberRequirements: MemberRequirements
  /** Whether pieces may be handed to, taken from and moved between members. */
  canAssign: boolean
  /** Whether a swap may be started for a piece. */
  canManageInventory: boolean
  /** Whether everything the member holds may be asked back at once. */
  canReturnEverything: boolean
}>()

const emit = defineEmits<{
  'request-exchange': [item: MyInventoryItem]
  unassign: [item: MyInventoryItem]
  reassign: [item: MyInventoryItem]
  'hand-out': [itemId: number]
  'plan-hand-out': [itemId: number, inventoryId: number]
  'hand-out-new': [inventoryId: number, sizeId: number | null]
  /** The member's gear changed outside the lists above, so they read again. */
  changed: []
}>()

const scan = useScanHandOut(toRef(props, 'memberId'), toRef(props, 'memberName'), () => emit('changed'))
const { scanValue, handOutMode, unknownCode } = scan

const handingOut = ref(false)
const handOutGap = ref<RequiredInventoryItem | null>(null)

/** Opens the hand-out for one gap, or for any piece where no gap is named. */
function openHandOut(requirement: RequiredInventoryItem | null) {
  handOutGap.value = requirement
  handingOut.value = true
}
</script>

<template>
  <div class="space-y-4">
    <ScanHandOutPanel
        v-if="canAssign"
        v-model:hand-out-mode="handOutMode"
        v-model:scan-value="scanValue"
        :scan-busy="scan.busy.value"
        :scan-failure="scan.scanFailure.value ?? scan.handoverFailure.value"
        :scan-success="scan.success.value"
        @submit="scan.submit"
        @decoded="scan.decoded"
    />

    <ReturnEverythingBar
        v-if="canReturnEverything && memberInventory.length > 0"
        :member-id="memberId"
        @done="emit('changed')"
    />

    <InventorySection
        :member-inventory="memberInventory"
        :requirements="memberRequirements.required"
        :can-assign="canAssign"
        :can-manage-inventory="canManageInventory"
        @assign-item="openHandOut(null)"
        @fill-gap="openHandOut"
        @request-exchange="emit('request-exchange', $event)"
        @unassign="emit('unassign', $event)"
        @reassign="emit('reassign', $event)"
    />

    <MovementsPanel :member-id="memberId" @changed="emit('changed')"/>

    <HandOutModal
        v-model="handingOut"
        :member-id="memberId"
        :requirement="handOutGap"
        @hand-out="emit('hand-out', $event)"
        @plan-hand-out="(itemId, inventoryId) => emit('plan-hand-out', itemId, inventoryId)"
        @hand-out-new="(inventoryId, sizeId) => emit('hand-out-new', inventoryId, sizeId)"
    />

    <UnknownScanModal
        v-if="unknownCode"
        :scanned-code="unknownCode"
        context="member"
        @created="scan.createdFromUnknown"
        @close="unknownCode = null"
    />
  </div>
</template>
