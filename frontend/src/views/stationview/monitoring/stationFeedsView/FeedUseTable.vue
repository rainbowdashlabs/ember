/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import MemberName from '@/components/avatar/MemberName.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'
import {emptyTableState, useDataTable} from '@/composables/useDataTable'
import {formatRelative} from '@/util/format'
import type {FeedUse} from '@/api/feedToken'
import {usedRecently} from './feedUse'

/**
 * The subscriptions of one station, searchable by name, sortable and filterable by every column.
 * Newest first to begin with: a subscription set up today is the one somebody is likely asking about.
 *
 * <p>A feed nobody fetches is the interesting row, so "never" is spelled out rather than left as
 * an empty cell.
 */
const props = defineProps<{
  uses: FeedUse[]
}>()

const {t} = useI18n()

const FeedState = {IN_USE: 'inUse', DORMANT: 'dormant'} as const

function fetched(stamp?: string | null): string {
  return stamp ? formatRelative(stamp) : t('stationFeeds.never')
}

function fetchedColumn(key: string, label: string, stamp: (use: FeedUse) => string | null | undefined): TableColumn<FeedUse> {
  return {key, label, type: ColumnTypes.DATE, value: stamp, display: use => fetched(stamp(use)), searchable: false}
}

const columns = computed<TableColumn<FeedUse>[]>(() => [
  {key: 'name', label: t('stationFeeds.column.member'), type: ColumnTypes.TEXT, value: use => use.identity?.name ?? '', pinned: true},
  {key: 'createdAt', label: t('stationFeeds.column.since'), type: ColumnTypes.DATE, value: use => use.createdAt},
  fetchedColumn('ical', t('stationFeeds.column.calendar'), use => use.icalPolledAt),
  fetchedColumn('notifications', t('stationFeeds.column.notifications'), use => use.notificationPolledAt),
  {
    key: 'state', label: t('stationFeeds.column.state'), type: ColumnTypes.ENUM, align: 'right', searchable: false,
    value: use => usedRecently(use) ? FeedState.IN_USE : FeedState.DORMANT,
    options: [
      {value: FeedState.IN_USE, label: t('stationFeeds.inUse')},
      {value: FeedState.DORMANT, label: t('stationFeeds.dormant')},
    ],
  },
])

const table = useDataTable<FeedUse>({
  id: 'station-feed-uses',
  rows: () => props.uses,
  columns,
  rowKey: use => use.memberId,
  state: ref(emptyTableState('createdAt', 'desc')),
})
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center gap-2">
      <SearchInput v-model="table.search" :placeholder="t('stationFeeds.searchPlaceholder')" class="flex-1"/>
      <TableColumnPicker :table="table"/>
    </div>

    <RecordTable :table="table" row-test-id="feed-use-row" test-id="feed-use-table">
      <template #cell-name="{row}">
        <MemberName :identity="row.identity"/>
      </template>
      <template #cell-ical="{row, text}">
        <span :class="{'text-(--text-muted)': !row.icalPolledAt}">{{ text }}</span>
      </template>
      <template #cell-notifications="{row, text}">
        <span :class="{'text-(--text-muted)': !row.notificationPolledAt}">{{ text }}</span>
      </template>
      <template #cell-state="{row, text}">
        <span :class="usedRecently(row) ? 'text-success' : 'text-(--text-muted)'">{{ text }}</span>
      </template>
      <template #empty>
        <MutedText tag="p" size="sm">{{ t('stationFeeds.noneFound') }}</MutedText>
      </template>
    </RecordTable>
  </div>
</template>
