/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup generic="Row">
import {computed} from 'vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RecordCardControls from './RecordCardControls.vue'
import RecordCell from './RecordCell.vue'
import type {DataTableApi} from '@/composables/useDataTable'

/**
 * The rows of a {@link RecordTable} as cards, for a phone.
 *
 * <p>The first column that shows heads the card and the others stand under it with their labels,
 * so the columns chosen for the table are the ones on the card. An empty cell is left off rather
 * than shown as a label with nothing beside it.
 */
const props = defineProps<{
  table: DataTableApi<Row>
  rows: readonly Row[]
  testId: string
  rowTestId: string
  clickable: boolean
  /** Whether the sort and filter controls stand above the cards, which only the whole table wants. */
  controls: boolean
  rowClass?: (row: Row) => string
}>()

const emit = defineEmits<{
  'row-click': [row: Row]
}>()

const heading = computed(() => props.table.visibleColumns[0])
const details = computed(() => props.table.visibleColumns.slice(1))
</script>

<template>
  <div :data-testid="testId" class="space-y-2">
    <RecordCardControls v-if="controls" :table="table"/>
    <NeutralContainer
        v-for="row in rows"
        :key="table.rowKey(row)"
        :class="rowClass?.(row)"
        :clickable="clickable"
        :data-testid="rowTestId"
        class="space-y-2"
        @click="clickable && emit('row-click', row)"
    >
      <div class="flex items-start justify-between gap-2">
        <div class="flex items-center gap-2 min-w-0 font-medium">
          <div v-if="$slots.lead" @click.stop><slot :row="row" name="lead"/></div>
          <RecordCell v-if="heading" v-slot="{text}" :column="heading" :row="row" :table="table">
            <slot :name="`cell-${heading.key}`" :row="row" :text="text">{{ text }}</slot>
          </RecordCell>
        </div>
        <div v-if="$slots.actions" class="flex gap-1" @click.stop><slot :row="row" name="actions"/></div>
      </div>
      <dl class="grid grid-cols-2 gap-x-4 gap-y-1 text-xs">
        <RecordCell v-for="column in details" :key="column.key" v-slot="{text}" :column="column" :row="row" :table="table">
          <div v-if="text">
            <dt class="text-(--text-muted)">{{ column.label }}</dt>
            <dd><slot :name="`cell-${column.key}`" :row="row" :text="text">{{ text }}</slot></dd>
          </div>
        </RecordCell>
      </dl>
      <slot :row="row" name="card-extra"/>
    </NeutralContainer>
    <slot v-if="rows.length === 0" name="empty"/>
  </div>
</template>
