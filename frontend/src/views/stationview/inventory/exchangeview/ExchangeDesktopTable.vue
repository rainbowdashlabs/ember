/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ExchangeStatusUpdatePanel from './ExchangeStatusUpdatePanel.vue'
import ExchangeTableHeader from './ExchangeTableHeader.vue'
import ExchangeTableRow from './ExchangeTableRow.vue'
import type { ExchangeRequestEntry, ExchangeStatusName, InventoryItem } from '@/api/types'

const props = defineProps<{
  requests: ExchangeRequestEntry[]
  exportMode: boolean
  showMemberColumn: boolean
  canManageExchanges: boolean
  selectedForExport: Set<number>
  updatingId: number | null
  availableItems: InventoryItem[]
  nextStatusesFor: (request: ExchangeRequestEntry) => ExchangeStatusName[]
}>()

const emit = defineEmits<{
  (e: 'toggle-select-all'): void
  (e: 'toggle-export', id: number): void
  (e: 'open-log', id: number): void
  (e: 'start-update', request: ExchangeRequestEntry): void
  (e: 'delete', id: number): void
  (e: 'status-done'): void
  (e: 'status-cancel'): void
  (e: 'status-error', msg: string): void
}>()

const allSelected = computed(() => props.selectedForExport.size === props.requests.length && props.requests.length > 0)

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
          @toggle-select-all="emit('toggle-select-all')"
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
        </template>
      </tbody>
    </table>
  </NeutralContainer>
</template>
