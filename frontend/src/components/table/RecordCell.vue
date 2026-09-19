/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup generic="Row">
import {computed} from 'vue'
import type {TableColumn} from './tableColumn'
import type {DataTableApi} from '@/composables/useDataTable'

/**
 * One cell's words, worked out once and handed to whatever draws the cell, so a row and a card
 * that both need them never word the same cell twice.
 */
const props = defineProps<{
  table: DataTableApi<Row>
  column: TableColumn<Row>
  row: Row
}>()

const text = computed(() => props.table.display(props.column, props.row))
</script>

<template>
  <slot :text="text"/>
</template>
