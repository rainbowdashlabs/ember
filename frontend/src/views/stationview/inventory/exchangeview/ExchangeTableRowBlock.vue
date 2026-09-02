/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ExchangeCorrectPanel from './ExchangeCorrectPanel.vue'
import ExchangeStatusUpdatePanel from './ExchangeStatusUpdatePanel.vue'
import ExchangeTableRow from './ExchangeTableRow.vue'
import type { ExchangeRequestEntry, ExchangeStatusName } from '@/api/exchanges'
import type { InventoryItem } from '@/api/inventory'

/**
 * One exchange in the table: its row, and the panel that opens underneath it.
 *
 * <p>The two panels are rows of the same table rather than something floating above it, because a
 * form that opens where the exchange stands keeps its place in the list; a dialogue over the table
 * would hide the neighbours the reader is comparing it against. They belong with the row rather
 * than with the table, which is what keeps the table itself readable.
 */
defineProps<{
  request: ExchangeRequestEntry
  exportMode: boolean
  showMemberColumn: boolean
  canManageExchanges: boolean
  selected: boolean
  colSpan: number
  updating: boolean
  correcting: boolean
  availableItems: InventoryItem[]
  nextStatuses: ExchangeStatusName[]
}>()

const emit = defineEmits<{
  (e: 'toggle-export'): void
  (e: 'open-log'): void
  (e: 'start-update'): void
  (e: 'start-correct'): void
  (e: 'delete'): void
  (e: 'status-done'): void
  (e: 'status-cancel'): void
  (e: 'status-error', msg: string): void
  (e: 'correct-done'): void
  (e: 'correct-cancel'): void
}>()
</script>

<template>
  <ExchangeTableRow
    :request="request"
    :export-mode="exportMode"
    :show-member-column="showMemberColumn"
    :can-manage-exchanges="canManageExchanges"
    :selected="selected"
    @toggle-export="emit('toggle-export')"
    @open-log="emit('open-log')"
    @start-update="emit('start-update')"
    @start-correct="emit('start-correct')"
    @delete="emit('delete')"
  />
  <tr v-if="updating" class="bg-(--bg-accent)/30">
    <td :colspan="colSpan" class="px-3 py-3">
      <ExchangeStatusUpdatePanel
        :request="request"
        :next-statuses="nextStatuses"
        :available-items="availableItems"
        @done="emit('status-done')"
        @cancel="emit('status-cancel')"
        @error="(msg) => emit('status-error', msg)"
      />
    </td>
  </tr>
  <tr v-if="correcting" class="bg-(--bg-accent)/30">
    <td :colspan="colSpan" class="px-3 py-3">
      <ExchangeCorrectPanel
        :request="request"
        @done="emit('correct-done')"
        @cancel="emit('correct-cancel')"
        @error="(msg) => emit('status-error', msg)"
      />
    </td>
  </tr>
</template>
