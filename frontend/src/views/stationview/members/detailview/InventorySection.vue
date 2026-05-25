/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import InventoryItemCard from '@/views/stationview/inventory/InventoryItemCard.vue'
import type { MyInventoryItem } from '@/api/inventory'
import type { ExchangeRequestEntry } from '@/api/types'

const { t } = useI18n()

defineProps<{
  memberInventory: MyInventoryItem[]
  memberExchanges: ExchangeRequestEntry[]
  showInventoryManagement: boolean
  canManageInventory: boolean
}>()

const emit = defineEmits<{
  assignItem: []
  requestExchange: [item: MyInventoryItem]
  unassign: [item: MyInventoryItem]
  reassign: [item: MyInventoryItem]
}>()

function itemExchange(itemId: number, exchanges: ExchangeRequestEntry[]): ExchangeRequestEntry | undefined {
  return exchanges.find(e => e.itemId === itemId)
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('memberDetail.inventory') }}</SubHeader>
      <PrimaryButton :icon="['fas', 'plus']" v-if="showInventoryManagement" @click="emit('assignItem')">
        {{ t('memberDetail.assignItem') }}
      </PrimaryButton>
    </div>
    <div v-if="memberInventory.length === 0" class="text-sm text-(--text-muted)">
      {{ t('memberDetail.noInventory') }}
    </div>
    <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
      <InventoryItemCard
          v-for="item in memberInventory"
          :key="item.id"
          :item="item"
          :exchange="itemExchange(item.id, memberExchanges) ?? null"
          :show-exchange-button="canManageInventory"
          :show-unassign-button="showInventoryManagement"
          :show-reassign-button="showInventoryManagement"
          :show-inventory-name="true"
          @request-exchange="emit('requestExchange', $event)"
          @unassign="emit('unassign', $event)"
          @reassign="emit('reassign', $event)"
      />
    </div>
  </NeutralContainer>
</template>
