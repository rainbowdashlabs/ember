/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup generic="Row">
import ColumnPickerButton from './ColumnPickerButton.vue'
import type {DataTableApi} from '@/composables/useDataTable'

/** The column list of a table built with {@link useDataTable}, wired to it. */
defineProps<{
  table: DataTableApi<Row>
  emptyLabel?: string
  fullWidth?: boolean
}>()
</script>

<template>
  <ColumnPickerButton
      :empty-label="emptyLabel"
      :full-width="fullWidth"
      :options="table.pickerOptions"
      @set-visible="table.setColumnsVisible"
      @toggle="table.toggleColumn"
  />
</template>
