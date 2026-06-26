/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ExchangeMobileList from './ExchangeMobileList.vue'
import ExchangeDesktopTable from './ExchangeDesktopTable.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import type { ExchangeRequestEntry, ExchangeStatusName, InventoryItem } from '@/api/types'

defineProps<{
  requests: ExchangeRequestEntry[]
  showMemberColumn: boolean
  canManageExchanges: boolean
  exportMode: boolean
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
    :updating-id="updatingId"
    :available-items="availableItems"
    :next-statuses-for="nextStatusesFor"
    @toggle-select-all="emit('toggle-select-all')"
    @toggle-export="(id) => emit('toggle-export', id)"
    @open-log="(id) => emit('open-log', id)"
    @start-update="(r) => emit('start-update', r)"
    @delete="(id) => emit('delete', id)"
    @status-done="emit('status-done')"
    @status-cancel="emit('status-cancel')"
    @status-error="(msg) => emit('status-error', msg)"
  />
</template>
