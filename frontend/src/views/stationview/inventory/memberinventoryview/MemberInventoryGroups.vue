/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import InventoryItemCard from '../InventoryItemCard.vue'
import type {ExchangeRequestEntry} from '@/api/exchanges'
import type {MyInventoryItem} from '@/api/inventory'

export interface InventoryGroup {
  inventoryId: number
  inventoryName: string
  items: MyInventoryItem[]
}

defineProps<{
  groups: InventoryGroup[]
  items: MyInventoryItem[]
  itemExchange: (itemId: number) => ExchangeRequestEntry | undefined
  showExchangeButton: boolean
}>()

defineEmits<{
  (e: 'request-exchange', item: MyInventoryItem): void
}>()

const {t} = useI18n()
</script>

<template>
  <EmptyState v-if="groups.length === 0 && items.length === 0">{{ t('profile.noInventory') }}</EmptyState>

  <div v-else class="space-y-6">
    <div v-for="group in groups" :key="group.inventoryId">
      <div class="flex items-center justify-between mb-2">
        <SubHeader>{{ group.inventoryName }}</SubHeader>
        <span class="text-sm text-(--text-muted)">{{ group.items.length }}</span>
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
            :show-exchange-button="showExchangeButton"
            @request-exchange="$emit('request-exchange', $event)"
        />
      </div>
    </div>
  </div>
</template>
