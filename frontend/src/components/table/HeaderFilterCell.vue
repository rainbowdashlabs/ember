/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup generic="Row">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import type {TableColumn} from './tableColumn'
import type {DataTableApi} from '@/composables/useDataTable'

/**
 * A column's title with its sort and filter controls beside it.
 *
 * <p>Both are real buttons so a column can be sorted and filtered from the keyboard. The filter
 * stops its click, because a header is often inside something that sorts when clicked.
 */
const props = defineProps<{
  table: DataTableApi<Row>
  column: TableColumn<Row>
}>()

const {t} = useI18n()

const sorted = computed(() => props.table.ariaSort(props.column.key) !== 'none')
const filtered = computed(() => props.table.hasFilter(props.column.key))
</script>

<template>
  <span class="inline-flex items-center gap-0.5">
    {{ column.label }}
    <IconButton
        v-if="table.isSortable(column)"
        :class="sorted ? 'text-primary' : 'text-(--text-muted) hover:text-primary'"
        :icon="['fas', table.sortIcon(column.key)]"
        :label="t('tableFilter.sortBy', {column: column.label})"
        class="-my-2 p-1!"
        data-testid="column-sort"
        @click="table.toggleSort(column.key)"
    />
    <IconButton
        v-if="table.isFilterable(column)"
        :class="filtered ? 'text-primary' : 'text-(--text-muted) hover:text-primary'"
        :icon="['fas', 'filter']"
        :label="t('tableFilter.by', {column: column.label})"
        class="-my-2 p-1!"
        data-testid="column-filter-open"
        @click.stop="table.openFilter(column.key)"
    />
  </span>
</template>
