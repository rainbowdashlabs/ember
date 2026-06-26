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
import type {ExchangeRequestEntry} from '@/api/types'
import type {MyInventoryItem} from '@/api/inventory'

interface InventoryGroup {
  inventoryId: number
  inventoryName: string
  requiredQuantity: number
  items: MyInventoryItem[]
}

const props = defineProps<{
  grouped: InventoryGroup[]
  items: MyInventoryItem[]
  activeExchanges: ExchangeRequestEntry[]
}>()

const emit = defineEmits<{
  requestExchange: [item: MyInventoryItem]
}>()

const {t} = useI18n()

function itemExchange(itemId: number): ExchangeRequestEntry | undefined {
  return props.activeExchanges.find(e => e.itemId === itemId)
}
</script>

<template>
  <EmptyState v-if="grouped.length === 0 && items.length === 0">{{ t('profile.noInventory') }}</EmptyState>

  <div v-else class="space-y-6">
    <div v-for="group in grouped" :key="group.inventoryId">
      <div class="flex items-center justify-between mb-2">
        <SubHeader>{{ group.inventoryName }}</SubHeader>
        <span v-if="group.requiredQuantity > 0" class="text-sm text-(--text-muted)">
          {{ group.items.length }} / {{ group.requiredQuantity }}
          <span v-if="group.items.length < group.requiredQuantity" class="text-error">
            ({{ group.requiredQuantity - group.items.length }} fehlt)
          </span>
        </span>
      </div>

      <MutedText tag="div" size="sm" class="py-2" v-if="group.items.length === 0">
        {{ t('profile.noInventory') }}
      </MutedText>

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
        <InventoryItemCard
            v-for="item in group.items"
            :key="item.id"
            :item="item"
            :exchange="itemExchange(item.id) ?? null"
            :show-exchange-button="true"
            @request-exchange="(i: MyInventoryItem) => emit('requestExchange', i)"
        />
      </div>
    </div>
  </div>
</template>
