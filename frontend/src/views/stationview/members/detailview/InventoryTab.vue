/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import InventorySection from './InventorySection.vue'
import MissingRequirements from './inventorytab/MissingRequirements.vue'
import type { MemberRequirements, MyInventoryItem } from '@/api/inventory'
import type { ExchangeRequestEntry } from '@/api/exchanges'

defineProps<{
  memberInventory: MyInventoryItem[]
  memberExchanges: ExchangeRequestEntry[]
  memberRequirements: MemberRequirements
  showInventoryManagement: boolean
  canManageInventory: boolean
  canEdit: boolean
}>()

defineEmits<{
  (e: 'assign-item'): void
  (e: 'request-exchange', item: MyInventoryItem): void
  (e: 'unassign', item: MyInventoryItem): void
  (e: 'reassign', item: MyInventoryItem): void
  (e: 'hand-out', itemId: number): void
  (e: 'hand-out-new', inventoryId: number, sizeId: number | null): void
}>()
</script>

<template>
  <div class="space-y-4">
    <MissingRequirements
        :requirements="memberRequirements"
        :can-hand-out="showInventoryManagement && canEdit"
        @hand-out="$emit('hand-out', $event)"
        @hand-out-new="(inventoryId, sizeId) => $emit('hand-out-new', inventoryId, sizeId)"
    />

    <InventorySection
        v-if="memberInventory.length > 0 || showInventoryManagement"
        :member-inventory="memberInventory"
        :member-exchanges="memberExchanges"
        :show-inventory-management="showInventoryManagement && canEdit"
        :can-manage-inventory="canManageInventory && canEdit"
        @assign-item="$emit('assign-item')"
        @request-exchange="$emit('request-exchange', $event)"
        @unassign="$emit('unassign', $event)"
        @reassign="$emit('reassign', $event)"
    />
  </div>
</template>
