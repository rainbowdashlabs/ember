/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ExchangeMobileList from './ExchangeMobileList.vue'
import ExchangeDesktopTable from './ExchangeDesktopTable.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import type { ExchangeRequestEntry, ExchangeStatusName } from '@/api/exchanges'
import type { InventoryItem } from '@/api/inventory'
import type { SortDirection } from '@/composables/useSortable'
import type { ExchangeSortKey } from './exchangeFilter'

defineProps<{
  requests: ExchangeRequestEntry[]
  showMemberColumn: boolean
  canManageExchanges: boolean
  exportMode: boolean
  selectedForExport: Set<number>
  allSelected: boolean
  updatingId: number | null
  availableItems: InventoryItem[]
  nextStatusesFor: (request: ExchangeRequestEntry) => ExchangeStatusName[]
  sortKey: ExchangeSortKey
  direction: SortDirection
}>()

const emit = defineEmits<{
  (e: 'toggle-select-all'): void
  (e: 'sort', key: ExchangeSortKey): void
  (e: 'toggle-export', id: number): void
  (e: 'open-log', id: number): void
  (e: 'start-update', request: ExchangeRequestEntry): void
  (e: 'delete', id: number): void
  (e: 'status-done'): void
  (e: 'status-cancel'): void
  (e: 'status-error', msg: string): void
}>()

const { isMobile } = useBreakpoint()
</script>

<template>
  <ExchangeMobileList
    v-if="isMobile"
    :requests="requests"
    :show-member-column="showMemberColumn"
    :can-manage-exchanges="canManageExchanges"
    :export-mode="exportMode"
    :selected-for-export="selectedForExport"
    :updating-id="updatingId"
    :available-items="availableItems"
    :next-statuses-for="nextStatusesFor"
    @toggle-export="(id) => emit('toggle-export', id)"
    @open-log="(id) => emit('open-log', id)"
    @start-update="(r) => emit('start-update', r)"
    @delete="(id) => emit('delete', id)"
    @status-done="emit('status-done')"
    @status-cancel="emit('status-cancel')"
    @status-error="(msg) => emit('status-error', msg)"
  />
  <ExchangeDesktopTable
    v-else
    :requests="requests"
    :export-mode="exportMode"
    :show-member-column="showMemberColumn"
    :can-manage-exchanges="canManageExchanges"
    :selected-for-export="selectedForExport"
    :all-selected="allSelected"
    :updating-id="updatingId"
    :available-items="availableItems"
    :next-statuses-for="nextStatusesFor"
    :sort-key="sortKey"
    :direction="direction"
    @toggle-select-all="emit('toggle-select-all')"
    @sort="(key) => emit('sort', key)"
    @toggle-export="(id) => emit('toggle-export', id)"
    @open-log="(id) => emit('open-log', id)"
    @start-update="(r) => emit('start-update', r)"
    @delete="(id) => emit('delete', id)"
    @status-done="emit('status-done')"
    @status-cancel="emit('status-cancel')"
    @status-error="(msg) => emit('status-error', msg)"
  />
</template>
