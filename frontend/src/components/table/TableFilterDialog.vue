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
const props = defineProps<{
  table: DataTableApi<Row>
}>()

const dialog = props.table.filterDialog
</script>

<template>
  <ColumnFilterModal
      v-model="dialog.open"
      :choices="dialog.choices"
      :column-label="dialog.label"
      :include-empty="dialog.includeEmpty"
      :kind="dialog.kind"
      :selected-values="dialog.selected"
      @apply="table.applyFilter"
  />
</template>
