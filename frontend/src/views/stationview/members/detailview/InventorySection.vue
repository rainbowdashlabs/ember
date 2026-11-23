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
import IncomingGearCard from './inventorytab/IncomingGearCard.vue'
import type { MovementResponse, MyInventoryItem, RequiredInventoryItem } from '@/api/generated/schema'
import { fillsAGap } from '@/util/movementPieces'

const { t } = useI18n()

/**
 * What the member holds and what they are still owed, one block per inventory.
 *
 * <p>A piece that is missing stands in its inventory's block beside the pieces that are there, so
 * the gap is seen where the reader looks for the gear, not in a list of its own above it. Inventories
 * the member holds nothing of yet still get a block when something is required of them.
 *
 * <p>A piece an open movement is bringing them stands there too, and fills a gap rather than standing
 * beside it: a gap that is already being filled offers no second hand-out. An exchange fills one only
 * once the piece it swaps has left the member, since until then they still hold it.
 */
const props = defineProps<{
  memberInventory: MyInventoryItem[]
  requirements: RequiredInventoryItem[]
  /** The open movements still on their way to the member. */
  incoming?: MovementResponse[]
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
  incoming: MovementResponse[]
  requirement: RequiredInventoryItem | null
  missing: number
}

/** The block of movements that name no inventory, which is rare and still worth showing. */
const NO_INVENTORY = 0

const groups = computed((): InventoryGroup[] => {
  const byInventory = new Map<number, InventoryGroup>()
  const groupOf = (inventoryId: number, inventoryName: string) => {
    const group = byInventory.get(inventoryId)
        ?? { inventoryId, inventoryName, items: [], incoming: [], requirement: null, missing: 0 }
    byInventory.set(inventoryId, group)
    return group
  }
  for (const item of props.memberInventory) {
    groupOf(item.inventoryId, item.inventoryName ?? '').items.push(item)
  }
  for (const movement of props.incoming ?? []) {
    const inventoryId = movement.inventoryId ?? NO_INVENTORY
    groupOf(inventoryId, movement.inventoryName ?? t('memberDetail.incomingElsewhere')).incoming.push(movement)
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

const heldIds = computed(() => new Set(props.memberInventory.map(item => item.id)))

/** The gaps nothing is on its way to fill yet, which are the ones still offering a hand-out. */
function openGaps(group: InventoryGroup): number {
  const filling = group.incoming.filter(movement => fillsAGap(movement, heldIds.value)).length
  return Math.max(0, group.missing - filling)
}

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
          <IncomingGearCard
              v-for="movement in group.incoming"
              :key="`incoming-${movement.id}`"
              :movement="movement"
          />
          <MissingSlotCard
              v-for="slot in openGaps(group)"
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
