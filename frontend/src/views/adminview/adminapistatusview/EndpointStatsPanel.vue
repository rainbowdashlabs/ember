/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import type {EndpointStats} from '@/api/apiStatus'
import {useDataTable} from '@/composables/useDataTable'
import {methodColor} from './apiStatusFormat'
import {endpointColumns} from './endpointColumns'

/**
 * The endpoints the server ranks slowest, fastest or most failing, one ranking at a time. A press
 * on an endpoint opens its own statistics.
 */
const props = defineProps<{
  slowest: EndpointStats[]
  fastest: EndpointStats[]
  failing: EndpointStats[]
}>()

type Ranking = 'slowest' | 'fastest' | 'failing'

const RANKING_LABEL_KEYS: Record<Ranking, string> = {
  slowest: 'apiStatus.slowest',
  fastest: 'apiStatus.fastest',
  failing: 'apiStatus.failing',
}

const {t} = useI18n()
const router = useRouter()

const ranking = ref<Ranking>('slowest')

const table = useDataTable<EndpointStats>({
  id: 'admin-api-status-endpoints',
  perStation: false,
  rows: computed(() => props[ranking.value]),
  columns: computed(() => endpointColumns(t)),
  rowKey: endpoint => `${endpoint.method} ${endpoint.path}`,
})

/**
 * The statistics of one endpoint. A row of a table cannot be a link itself, so the address hangs on
 * the cell that names the endpoint and the rest of the row answers a press as it always did.
 */
function detailPage(endpoint: EndpointStats) {
  return {name: 'admin-api-status-detail', query: {method: endpoint.method, path: endpoint.path}}
}

function openDetail(endpoint: EndpointStats) {
  router.push(detailPage(endpoint))
}

function errorRateClass(endpoint: EndpointStats): string {
  return endpoint.errorRate > 0.1 ? 'text-red-500 font-semibold' : ''
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center gap-2">
      <SelectionToggleButton
          v-for="(labelKey, name) in RANKING_LABEL_KEYS"
          :key="name"
          :selected="ranking === name"
          @toggle="ranking = name"
      >
        {{ t(labelKey) }}
      </SelectionToggleButton>
      <div class="ml-auto">
        <TableColumnPicker :table="table"/>
      </div>
    </div>
    <RecordTable :table="table" clickable test-id="api-status-endpoints" @row-click="openDetail">
      <template #cell-method="{row}">
        <span :class="methodColor(row.method)" class="font-mono text-xs font-semibold">{{ row.method }}</span>
      </template>
      <template #cell-path="{row}">
        <RowLink :to="detailPage(row)">
          <span class="font-mono text-xs">{{ row.path }}</span>
        </RowLink>
      </template>
      <template #cell-errorRate="{row, text}">
        <span :class="errorRateClass(row)" class="tabular-nums">{{ text }}</span>
      </template>
    </RecordTable>
  </div>
</template>
