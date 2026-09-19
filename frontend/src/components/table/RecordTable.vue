/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup generic="Row">
import {computed} from 'vue'
import RecordTableCards from './RecordTableCards.vue'
import RecordTableDesktop from './RecordTableDesktop.vue'
import TableFilterDialog from './TableFilterDialog.vue'
import {useBreakpoint} from '@/composables/useBreakpoint'
import type {DataTableApi} from '@/composables/useDataTable'

/**
 * A table of records drawn from {@link useDataTable}: a header that sorts and filters every column
 * by its type, and one card per record on a phone, showing the same columns.
 *
 * <p>Cells are written as the column words them unless the screen fills the slot named
 * `cell-<key>`. Around the columns a screen may add a leading cell (`lead`, for a checkbox or an
 * icon), a trailing one (`actions`), a line under a row (`after-row`, for a row opened up), and
 * something under a card's cells (`card-extra`). Each slot is handed the `row`.
 *
 * <p>A screen that shows one table in parts, a block per group, passes each block its `subset` of
 * the rows. A part brings neither the filter dialog nor the phone's sort controls, which the screen
 * then places once for the whole.
 */
const props = withDefaults(defineProps<{
  table: DataTableApi<Row>
  /** The rows of this part, already sorted and filtered by the table. All of them where absent. */
  subset?: readonly Row[]
  testId?: string
  /** What each row answers to in a test, where a screen's tests already name its rows. */
  rowTestId?: string
  /** Without the container around it, for a table that already sits in one. */
  plain?: boolean
  /** Whether the rows answer a press, which also marks them as pressable. */
  clickable?: boolean
  rowClass?: (row: Row) => string
}>(), {
  subset: undefined,
  testId: 'record-table',
  rowTestId: 'record-row',
  plain: false,
  clickable: false,
  rowClass: undefined,
})

const emit = defineEmits<{
  'row-click': [row: Row]
}>()

const {isMobile} = useBreakpoint()

const rows = computed(() => props.subset ?? props.table.rows)
const whole = computed(() => props.subset === undefined)
</script>

<template>
  <div>
    <RecordTableCards
        v-if="isMobile"
        :clickable="clickable"
        :controls="whole"
        :row-class="rowClass"
        :row-test-id="rowTestId"
        :rows="rows"
        :table="table"
        :test-id="testId"
        @row-click="emit('row-click', $event)"
    >
      <template v-for="(_, name) in $slots" :key="name" #[name]="scope">
        <slot :name="name" v-bind="scope ?? {}"/>
      </template>
    </RecordTableCards>
    <RecordTableDesktop
        v-else
        :clickable="clickable"
        :plain="plain"
        :row-class="rowClass"
        :row-test-id="rowTestId"
        :rows="rows"
        :table="table"
        :test-id="testId"
        @row-click="emit('row-click', $event)"
    >
      <template v-for="(_, name) in $slots" :key="name" #[name]="scope">
        <slot :name="name" v-bind="scope ?? {}"/>
      </template>
    </RecordTableDesktop>
    <TableFilterDialog v-if="whole" :table="table"/>
  </div>
</template>
