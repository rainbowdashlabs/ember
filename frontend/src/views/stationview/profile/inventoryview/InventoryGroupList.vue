/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import InventoryItemCard from '@/views/stationview/inventory/InventoryItemCard.vue'
import IncomingGearCard from '@/views/stationview/members/detailview/inventorytab/IncomingGearCard.vue'
import {StationModule, type MovementResponse, type MyInventoryItem} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import {fillsAGap} from '@/util/movementPieces'

/**
 * One inventory of the member's gear. The count is the station's: what it counts as held, which takes in
 * a piece handed in for a running exchange and leaves out a missing one, so the gap reads the same here
 * as on the station's screens.
 */
interface InventoryGroup {
  inventoryId: number
  inventoryName: string
  requiredQuantity: number
  heldQuantity: number
  items: MyInventoryItem[]
  incoming?: MovementResponse[]
}

defineProps<{
  grouped: InventoryGroup[]
  items: MyInventoryItem[]
}>()

const emit = defineEmits<{
  requestExchange: [item: MyInventoryItem]
  reportLost: [item: MyInventoryItem]
}>()

const {t} = useI18n()
const {isModuleEnabled} = useSession()

/** The gap the member still has, less what a movement is already bringing to fill it. */
function gapOf(group: InventoryGroup): number {
  const held = new Set(group.items.map(item => item.id))
  const filling = (group.incoming ?? []).filter(movement => fillsAGap(movement, held)).length
  return Math.max(0, group.requiredQuantity - group.heldQuantity - filling)
}
</script>

<template>
  <EmptyState v-if="grouped.length === 0 && items.length === 0">{{ t('profile.noInventory') }}</EmptyState>

  <div v-else class="space-y-6">
    <div v-for="group in grouped" :key="group.inventoryId">
      <div class="flex items-center justify-between mb-2">
        <SubHeader>{{ group.inventoryName }}</SubHeader>
        <span v-if="group.requiredQuantity > 0" class="text-sm text-(--text-muted)">
          {{ group.heldQuantity }} / {{ group.requiredQuantity }}
          <span v-if="gapOf(group) > 0" class="text-error" data-testid="group-gap">
            {{ t('memberDetail.missingCount', {count: gapOf(group)}) }}
          </span>
        </span>
      </div>

      <MutedText v-if="group.items.length === 0 && !group.incoming?.length" tag="div" size="sm" class="py-2">
        {{ t('profile.noInventory') }}
      </MutedText>

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
        <InventoryItemCard
            v-for="item in group.items"
            :key="item.id"
            :item="item"
            :show-exchange-button="isModuleEnabled(StationModule.INVENTORY_MOVEMENTS)"
            :show-lost-button="true"
            @request-exchange="(i: MyInventoryItem) => emit('requestExchange', i)"
            @report-lost="(i: MyInventoryItem) => emit('reportLost', i)"
        />
        <IncomingGearCard
            v-for="movement in group.incoming ?? []"
            :key="`incoming-${movement.id}`"
            :movement="movement"
        />
      </div>
    </div>
  </div>
</template>
