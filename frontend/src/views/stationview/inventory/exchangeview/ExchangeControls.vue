/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ExportFieldPicker from '@/components/export/ExportFieldPicker.vue'
import ExchangeToolbar from './ExchangeToolbar.vue'
import ExchangeFilterBar from './ExchangeFilterBar.vue'
import type { ExportFieldOption } from '@/composables/useExport'
import type { ExchangeSortKey, InventoryChoice } from './exchangeFilter'

/**
 * Everything standing above the list: what can be done with it, how it is narrowed down, and which
 * profile fields an export carries.
 */
defineProps<{
  exportMode: boolean
  exporting: boolean
  selectedCount: number
  canExport: boolean
  /** Whether narrowing the list is worth offering, which it is not while there is nothing in it. */
  showFilters: boolean
  inventories: InventoryChoice[]
  sortKey: ExchangeSortKey
  showSort: boolean
  exportFieldOptions: ExportFieldOption[]
  selectedExportFields: Set<string>
}>()

const emit = defineEmits<{
  (e: 'export'): void
  (e: 'cancel-export'): void
  (e: 'enter-export'): void
  (e: 'create'): void
  (e: 'sort', key: ExchangeSortKey): void
  (e: 'toggle-column', key: string): void
}>()

const search = defineModel<string>('search', {required: true})
const inventoryId = defineModel<string>('inventoryId', {required: true})
const status = defineModel<string>('status', {required: true})

const { t } = useI18n()
</script>

<template>
  <ExchangeToolbar
      :export-mode="exportMode" :exporting="exporting" :selected-count="selectedCount" :can-export="canExport"
      @export="emit('export')" @cancel-export="emit('cancel-export')"
      @enter-export="emit('enter-export')" @create="emit('create')"
  />

  <ExchangeFilterBar
      v-if="showFilters"
      v-model:search="search" v-model:inventory-id="inventoryId" v-model:status="status"
      :inventories="inventories" :sort-key="sortKey" :show-sort="showSort"
      @sort="emit('sort', $event)"
  />

  <ExportFieldPicker
      v-if="exportMode && exportFieldOptions.length > 0"
      boxed layout="inline"
      :label="t('exchanges.exportFieldsHint')"
      :options="exportFieldOptions"
      :selected="selectedExportFields"
      @toggle="emit('toggle-column', String($event))"
  />
</template>
