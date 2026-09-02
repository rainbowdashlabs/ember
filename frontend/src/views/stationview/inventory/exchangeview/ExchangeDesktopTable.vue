/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ExchangeTableHeader from './ExchangeTableHeader.vue'
import ExchangeTableRowBlock from './ExchangeTableRowBlock.vue'
import type { ExchangeRequestEntry, ExchangeStatusName } from '@/api/exchanges'
import type { InventoryItem } from '@/api/inventory'
import type { SortDirection } from '@/composables/useSortable'
import type { ExchangeSortKey } from './exchangeFilter'

const props = defineProps<{
  requests: ExchangeRequestEntry[]
  exportMode: boolean
  showMemberColumn: boolean
  canManageExchanges: boolean
  selectedForExport: Set<number>
  allSelected: boolean
  updatingId: number | null
  correctingId: number | null
  availableItems: InventoryItem[]
  nextStatusesFor: (request: ExchangeRequestEntry) => ExchangeStatusName[]
  sortKey: ExchangeSortKey
  direction: SortDirection
}>()

const emit = defineEmits<{
  (e: 'toggle-select-all'): void
  (e: 'toggle-export', id: number): void
  (e: 'open-log', id: number): void
  (e: 'start-update', request: ExchangeRequestEntry): void
  (e: 'start-correct', request: ExchangeRequestEntry): void
  (e: 'delete', id: number): void
  (e: 'status-done'): void
  (e: 'status-cancel'): void
  (e: 'status-error', msg: string): void
  (e: 'sort', key: ExchangeSortKey): void
  (e: 'correct-done'): void
  (e: 'correct-cancel'): void
}>()

const colSpan = computed(() => (props.showMemberColumn ? 9 : 8) + (props.exportMode ? 1 : 0))
</script>

<template>
  <NeutralContainer class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
        <ExchangeTableHeader
          :export-mode="exportMode"
          :show-member-column="showMemberColumn"
          :can-manage-exchanges="canManageExchanges"
          :all-selected="allSelected"
          :sort-key="sortKey"
          :direction="direction"
          @toggle-select-all="emit('toggle-select-all')"
          @sort="(key) => emit('sort', key)"
        />
      </thead>
      <tbody>
        <ExchangeTableRowBlock
          v-for="req in requests"
          :key="req.id"
          :request="req"
          :export-mode="exportMode"
          :show-member-column="showMemberColumn"
          :can-manage-exchanges="canManageExchanges"
          :selected="selectedForExport.has(req.id)"
          :col-span="colSpan"
          :updating="updatingId === req.id"
          :correcting="correctingId === req.id"
          :available-items="availableItems"
          :next-statuses="nextStatusesFor(req)"
          @toggle-export="emit('toggle-export', req.id)"
          @open-log="emit('open-log', req.id)"
          @start-update="emit('start-update', req)"
          @start-correct="emit('start-correct', req)"
          @delete="emit('delete', req.id)"
          @status-done="emit('status-done')"
          @status-cancel="emit('status-cancel')"
          @status-error="(msg) => emit('status-error', msg)"
          @correct-done="emit('correct-done')"
          @correct-cancel="emit('correct-cancel')"
        />
      </tbody>
    </table>
  </NeutralContainer>
</template>
