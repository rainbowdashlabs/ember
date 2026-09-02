/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ExchangeCorrectPanel from './ExchangeCorrectPanel.vue'
import ExchangeStatusUpdatePanel from './ExchangeStatusUpdatePanel.vue'
import ExchangeTableHeader from './ExchangeTableHeader.vue'
import ExchangeTableRow from './ExchangeTableRow.vue'
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
        <template v-for="req in requests" :key="req.id">
          <ExchangeTableRow
            :request="req"
            :export-mode="exportMode"
            :show-member-column="showMemberColumn"
            :can-manage-exchanges="canManageExchanges"
            :selected="selectedForExport.has(req.id)"
            @toggle-export="emit('toggle-export', req.id)"
            @open-log="emit('open-log', req.id)"
            @start-update="emit('start-update', req)"
            @start-correct="emit('start-correct', req)"
            @delete="emit('delete', req.id)"
          />
          <tr v-if="updatingId === req.id" class="bg-(--bg-accent)/30">
            <td :colspan="colSpan" class="px-3 py-3">
              <ExchangeStatusUpdatePanel
                :request="req"
                :next-statuses="nextStatusesFor(req)"
                :available-items="availableItems"
                @done="emit('status-done')"
                @cancel="emit('status-cancel')"
                @error="(msg) => emit('status-error', msg)"
              />
            </td>
          </tr>
          <tr v-if="correctingId === req.id" class="bg-(--bg-accent)/30">
            <td :colspan="colSpan" class="px-3 py-3">
              <ExchangeCorrectPanel
                :request="req"
                @done="emit('correct-done')"
                @cancel="emit('correct-cancel')"
                @error="(msg) => emit('status-error', msg)"
              />
            </td>
          </tr>
        </template>
      </tbody>
    </table>
  </NeutralContainer>
</template>
