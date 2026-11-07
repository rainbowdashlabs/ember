/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import InventoryItemCard from '@/views/stationview/inventory/InventoryItemCard.vue'
import MissingSlotCard from './inventorytab/MissingSlotCard.vue'
import type { MyInventoryItem, RequiredInventoryItem } from '@/api/generated/schema'

const { t } = useI18n()

/**
 * What the member holds and what they are still owed, one block per inventory.
 *
 * <p>A piece that is missing stands in its inventory's block beside the pieces that are there, so
 * the gap is seen where the reader looks for the gear, not in a list of its own above it. Inventories
 * the member holds nothing of yet still get a block when something is required of them.
 */
const props = defineProps<{
  memberInventory: MyInventoryItem[]
  requirements: RequiredInventoryItem[]
  canAssign: boolean
  canManageInventory: boolean
}>()

const emit = defineEmits<{
  assignItem: []
  fillGap: [requirement: RequiredInventoryItem]
  requestExchange: [item: MyInventoryItem]
  unassign: [item: MyInventoryItem]
  reassign: [item: MyInventoryItem]
}>()

interface InventoryGroup {
  inventoryId: number
  inventoryName: string
  items: MyInventoryItem[]
  requirement: RequiredInventoryItem | null
  missing: number
}

const groups = computed((): InventoryGroup[] => {
  const byInventory = new Map<number, InventoryGroup>()
  const groupOf = (inventoryId: number, inventoryName: string) => {
    const group = byInventory.get(inventoryId)
        ?? { inventoryId, inventoryName, items: [], requirement: null, missing: 0 }
    byInventory.set(inventoryId, group)
    return group
  }
  for (const item of props.memberInventory) {
    groupOf(item.inventoryId, item.inventoryName ?? '').items.push(item)
  }
  for (const requirement of props.requirements) {
    const missing = requirement.requiredQuantity - requirement.assignedQuantity
    if (missing <= 0 && !byInventory.has(requirement.inventoryId)) continue
    const group = groupOf(requirement.inventoryId, requirement.inventoryName)
    group.requirement = requirement
    group.missing = Math.max(0, missing)
  }
  return [...byInventory.values()]
})

function countOf(group: InventoryGroup): string {
  return group.requirement
      ? `${group.requirement.assignedQuantity} / ${group.requirement.requiredQuantity}`
      : String(group.items.length)
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('memberDetail.inventory') }}</SubHeader>
      <PrimaryButton v-if="canAssign" :icon="['fas', 'plus']" @click="emit('assignItem')">
        {{ t('memberDetail.assignItem') }}
      </PrimaryButton>
    </div>
    <div v-if="groups.length === 0" class="text-sm text-(--text-muted)">
      {{ t('memberDetail.noInventory') }}
    </div>
    <template v-else>
      <div v-for="group in groups" :key="group.inventoryId" class="space-y-2" data-testid="member-inventory-group">
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium">{{ group.inventoryName }}</span>
          <span class="text-sm text-(--text-muted)">
            {{ countOf(group) }}
            <span v-if="group.missing > 0" class="text-error">{{ t('memberDetail.missingCount', { count: group.missing }) }}</span>
          </span>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
          <InventoryItemCard
              v-for="item in group.items"
              :key="item.id"
              :item="item"
              :show-exchange-button="canManageInventory"
              :show-unassign-button="canAssign"
              :show-reassign-button="canAssign"
              @request-exchange="emit('requestExchange', $event)"
              @unassign="emit('unassign', $event)"
              @reassign="emit('reassign', $event)"
          />
          <MissingSlotCard
              v-for="slot in group.missing"
              :key="`missing-${slot}`"
              :inventory-name="group.inventoryName"
              :can-hand-out="canAssign"
              @hand-out="group.requirement && emit('fillGap', group.requirement)"
          />
        </div>
      </div>
    </template>
  </NeutralContainer>
</template>
