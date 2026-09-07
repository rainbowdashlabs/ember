/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {filterableStatuses, type ExchangeSortKey, type InventoryChoice} from './exchangeFilter'

const props = defineProps<{
  inventories: InventoryChoice[]
  sortKey: ExchangeSortKey
  /** Whether the ordering is picked here, which is what a screen without column headers needs. */
  showSort: boolean
}>()

const emit = defineEmits<{
  (e: 'sort', key: ExchangeSortKey): void
}>()

const search = defineModel<string>('search', {required: true})
const inventoryIds = defineModel<string[]>('inventoryIds', {required: true})
const statuses = defineModel<string[]>('statuses', {required: true})

const sortKeys: ExchangeSortKey[] = ['date', 'member', 'inventory', 'status']

const {t} = useI18n()

const inventoryOptions = computed(() => props.inventories.map(inv => ({value: String(inv.id), label: inv.name})))

const statusOptions = computed(() => filterableStatuses.map(name => ({value: name, label: t(`exchanges.status.${name}`)})))
</script>

<template>
  <div class="flex flex-wrap gap-3 items-end">
    <div class="flex-1 min-w-48 space-y-1">
      <FieldLabel>{{ t('exchanges.colMember') }}</FieldLabel>
      <SearchInput v-model="search" data-testid="exchange-filter-name" :placeholder="t('exchanges.filterNamePlaceholder')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('exchanges.colInventory') }}</FieldLabel>
      <MultiSelectDropdown
          v-model="inventoryIds"
          data-testid="exchange-filter-inventory"
          :options="inventoryOptions"
          :placeholder="t('exchanges.allInventories')"
      />
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('exchanges.colStatus') }}</FieldLabel>
      <MultiSelectDropdown
          v-model="statuses"
          data-testid="exchange-filter-status"
          :options="statusOptions"
          :placeholder="t('exchanges.filterAllStatuses')"
      />
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
