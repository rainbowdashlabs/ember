/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import ColumnFilterModal from '@/components/table/ColumnFilterModal.vue'
import type { ItemTableApi } from './useItemTable'

defineProps<{
  table: ItemTableApi
}>()
</script>

<template>
  <ColumnFilterModal
      v-model="table.filterModalOpen"
      :column-label="table.filterModalLabel"
      :values="table.filterModalValues"
      :selected-values="table.filterModalSelected"
      :include-empty="table.filterModalIncludeEmpty"
      @apply="table.applyColumnFilter"
      @close="table.filterModalOpen = false"
  />
</template>
