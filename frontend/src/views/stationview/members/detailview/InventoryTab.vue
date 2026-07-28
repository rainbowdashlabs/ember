/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import InventorySection from './InventorySection.vue'
import type { MyInventoryItem } from '@/api/inventory'
import type { ExchangeRequestEntry } from '@/api/exchanges'

defineProps<{
  memberInventory: MyInventoryItem[]
  memberExchanges: ExchangeRequestEntry[]
  showInventoryManagement: boolean
  canManageInventory: boolean
  canEdit: boolean
}>()

defineEmits<{
  (e: 'assign-item'): void
  (e: 'request-exchange', item: MyInventoryItem): void
  (e: 'unassign', item: MyInventoryItem): void
  (e: 'reassign', item: MyInventoryItem): void
}>()
</script>

<template>
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
</template>
