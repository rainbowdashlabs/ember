/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ExchangeMobileCard from './ExchangeMobileCard.vue'
import type { ExchangeRequestEntry, ExchangeStatusName } from '@/api/exchanges'
import type { InventoryItem } from '@/api/inventory'

defineProps<{
  requests: ExchangeRequestEntry[]
  showMemberColumn: boolean
  canManageExchanges: boolean
  exportMode: boolean
  selectedForExport: Set<number>
  updatingId: number | null
  correctingId: number | null
  availableItems: InventoryItem[]
  nextStatusesFor: (request: ExchangeRequestEntry) => ExchangeStatusName[]
}>()

const emit = defineEmits<{
  (e: 'toggle-export', id: number): void
  (e: 'open-log', id: number): void
  (e: 'start-update', request: ExchangeRequestEntry): void
  (e: 'start-correct', request: ExchangeRequestEntry): void
  (e: 'delete', id: number): void
  (e: 'status-done'): void
  (e: 'status-cancel'): void
  (e: 'status-error', msg: string): void
  (e: 'correct-done'): void
  (e: 'correct-cancel'): void
}>()
</script>

<template>
  <div class="space-y-3">
    <ExchangeMobileCard
      v-for="req in requests"
      :key="req.id"
      :request="req"
      :show-member-column="showMemberColumn"
      :can-manage-exchanges="canManageExchanges"
      :export-mode="exportMode"
      :selected="selectedForExport.has(req.id)"
      :is-updating="updatingId === req.id"
      :is-correcting="correctingId === req.id"
      :next-statuses="nextStatusesFor(req)"
      :available-items="availableItems"
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
  </div>
</template>
