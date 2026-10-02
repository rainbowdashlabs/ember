/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import InventorySection from './InventorySection.vue'
import MissingRequirements from './inventorytab/MissingRequirements.vue'
import type { MemberRequirements, MyInventoryItem } from '@/api/generated/schema'

defineProps<{
  memberInventory: MyInventoryItem[]
  memberRequirements: MemberRequirements
  showInventoryManagement: boolean
  canManageInventory: boolean
  canEdit: boolean
}>()

defineEmits<{
  'assign-item': []
  'request-exchange': [item: MyInventoryItem]
  unassign: [item: MyInventoryItem]
  reassign: [item: MyInventoryItem]
  'hand-out': [itemId: number]
  'hand-out-new': [inventoryId: number, sizeId: number | null]
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
        :show-inventory-management="showInventoryManagement && canEdit"
        :can-manage-inventory="canManageInventory && canEdit"
        @assign-item="$emit('assign-item')"
        @request-exchange="$emit('request-exchange', $event)"
        @unassign="$emit('unassign', $event)"
        @reassign="$emit('reassign', $event)"
    />
  </div>
</template>
