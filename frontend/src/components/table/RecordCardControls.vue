/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup generic="Row">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {DataTableApi} from '@/composables/useDataTable'

/**
 * The sort and the filters of a table shown as cards, which have no header to put them in.
 *
 * <p>The same columns sort and filter here as in the header of the table, so a reader on a phone
 * can narrow the list as far as one at a desk.
 */
const props = defineProps<{
  table: DataTableApi<Row>
}>()

const {t} = useI18n()

const sortable = computed(() => props.table.visibleColumns.filter(column => props.table.isSortable(column)))
const filterable = computed(() => props.table.visibleColumns.filter(column => props.table.isFilterable(column)))

const sortKey = computed({
  get: () => props.table.sortKey ?? '',
  set: (key: string | number | null | undefined) => { props.table.sortKey = key ? String(key) : null },
})

function flipDirection() {
  props.table.sortDirection = props.table.sortDirection === 'asc' ? 'desc' : 'asc'
}
</script>

<template>
  <div class="flex items-center gap-2" data-testid="record-card-controls">
    <SelectInput v-model="sortKey" :aria-label="t('tableFilter.sortLabel')" class="flex-1">
      <option value="">{{ t('tableFilter.unsorted') }}</option>
      <option v-for="column in sortable" :key="column.key" :value="column.key">{{ column.label }}</option>
    </SelectInput>
    <IconButton
        :icon="['fas', table.sortDirection === 'asc' ? 'sort-up' : 'sort-down']"
        :label="t('tableFilter.direction')"
        class="text-(--text-muted)"
        @click="flipDirection"
    />
    <ActionsMenu
        v-if="filterable.length > 0"
        :icon="['fas', 'filter']"
        :label="t('tableFilter.filterColumns')"
        test-id="record-card-filters"
    >
      <DropdownMenuItem
          v-for="column in filterable"
          :key="column.key"
          :icon="['fas', table.hasFilter(column.key) ? 'check' : 'filter']"
          @click="table.openFilter(column.key)"
      >
        {{ column.label }}
      </DropdownMenuItem>
    </ActionsMenu>
  </div>
</template>
