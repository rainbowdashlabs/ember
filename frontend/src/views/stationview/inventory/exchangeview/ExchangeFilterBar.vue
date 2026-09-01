/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {ALL_STATUSES, OPEN_STATUSES, statusChain, type ExchangeSortKey, type InventoryChoice} from './exchangeFilter'

defineProps<{
  inventories: InventoryChoice[]
  sortKey: ExchangeSortKey
  /** Whether the ordering is picked here, which is what a screen without column headers needs. */
  showSort: boolean
}>()

const emit = defineEmits<{
  (e: 'sort', key: ExchangeSortKey): void
}>()

const search = defineModel<string>('search', {required: true})
const inventoryId = defineModel<string>('inventoryId', {required: true})
const status = defineModel<string>('status', {required: true})

const sortKeys: ExchangeSortKey[] = ['date', 'member', 'inventory', 'status']

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap gap-3 items-end">
    <div class="flex-1 min-w-48 space-y-1">
      <FieldLabel>{{ t('exchanges.colMember') }}</FieldLabel>
      <SearchInput v-model="search" data-testid="exchange-filter-name" :placeholder="t('exchanges.filterNamePlaceholder')"/>
    </div>
    <div class="w-48 space-y-1">
      <FieldLabel>{{ t('exchanges.colInventory') }}</FieldLabel>
      <SelectInput v-model="inventoryId" data-testid="exchange-filter-inventory" class="w-full">
        <option value="">{{ t('exchanges.allInventories') }}</option>
        <option v-for="inv in inventories" :key="inv.id" :value="String(inv.id)">{{ inv.name }}</option>
      </SelectInput>
    </div>
    <div class="w-48 space-y-1">
      <FieldLabel>{{ t('exchanges.colStatus') }}</FieldLabel>
      <SelectInput v-model="status" data-testid="exchange-filter-status" class="w-full">
        <option :value="OPEN_STATUSES">{{ t('exchanges.filterOpen') }}</option>
        <option :value="ALL_STATUSES">{{ t('exchanges.filterAllStatuses') }}</option>
        <option v-for="name in statusChain" :key="name" :value="name">{{ t(`exchanges.status.${name}`) }}</option>
      </SelectInput>
    </div>
    <div v-if="showSort" class="w-48 space-y-1">
      <FieldLabel>{{ t('exchanges.sortLabel') }}</FieldLabel>
      <SelectInput
          :model-value="sortKey"
          data-testid="exchange-sort"
          class="w-full"
          @update:model-value="emit('sort', $event as ExchangeSortKey)"
      >
        <option v-for="key in sortKeys" :key="key" :value="key">{{ t(`exchanges.sortBy.${key}`) }}</option>
      </SelectInput>
    </div>
  </div>
</template>
