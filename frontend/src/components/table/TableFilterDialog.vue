/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup generic="Row">
import ColumnFilterModal from './ColumnFilterModal.vue'
import type {DataTableApi} from '@/composables/useDataTable'

/**
 * The filter dialog of one table. A {@link RecordTable} brings its own; a screen drawing a table in
 * several parts places this once, so one column's filter never opens twice.
 */
defineProps<{
  table: DataTableApi<Row>
}>()
</script>

<template>
  <ColumnFilterModal
      v-if="table.filterDialog"
      :choices="table.filterDialog.choices"
      :column-label="table.filterDialog.label"
      :include-empty="table.filterDialog.includeEmpty"
      :kind="table.filterDialog.kind"
      :model-value="true"
      :selected-values="table.filterDialog.selected"
      @apply="(selected, includeEmpty) => table.setFilter(table.filterDialog!.key, selected, includeEmpty)"
      @update:model-value="table.closeFilter()"
  />
</template>
