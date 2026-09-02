/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Th from '@/components/table/Th.vue'
import THead from '@/components/table/THead.vue'
import SortableHeader from '@/components/table/SortableHeader.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import type { SortDirection, SortKey } from '@/composables/useSortable'
import type { ExchangeSortKey } from './exchangeFilter'

const { t } = useI18n()

defineProps<{
  exportMode: boolean
  showMemberColumn: boolean
  canManageExchanges: boolean
  allSelected: boolean
  sortKey: ExchangeSortKey
  direction: SortDirection
}>()

const emit = defineEmits<{
  (e: 'toggle-select-all'): void
  (e: 'sort', key: ExchangeSortKey): void
}>()

function onSort(key: SortKey) {
  emit('sort', key as ExchangeSortKey)
}
</script>

<template>
  <THead>
    <th v-if="exportMode" class="px-1 py-2 w-8">
      <CheckboxInput :model-value="allSelected" data-testid="exchange-select-all" @update:model-value="emit('toggle-select-all')" />
    </th>
    <SortableHeader
        v-if="showMemberColumn"
        :label="t('exchanges.colMember')" sort-key="member" :active-key="sortKey" :direction="direction"
        @sort="onSort"
    />
    <SortableHeader
        :label="t('exchanges.colInventory')" sort-key="inventory" :active-key="sortKey" :direction="direction"
        @sort="onSort"
    />
    <Th v-if="canManageExchanges">{{ t('exchanges.colType') }}</Th>
    <Th>{{ t('exchanges.colOldSize') }}</Th>
    <Th>{{ t('exchanges.colNewSize') }}</Th>
    <SortableHeader
        :label="t('exchanges.colStatus')" sort-key="status" :active-key="sortKey" :direction="direction"
        @sort="onSort"
    />
    <Th>{{ t('exchanges.colReason') }}</Th>
    <SortableHeader
        :label="t('exchanges.colDate')" sort-key="date" :active-key="sortKey" :direction="direction"
        @sort="onSort"
    />
    <th class="px-3 py-2"></th>
  </THead>
</template>
