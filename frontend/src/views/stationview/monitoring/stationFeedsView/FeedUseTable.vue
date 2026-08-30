/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import DataTable from '@/components/table/DataTable.vue'
import SortableHeader from '@/components/table/SortableHeader.vue'
import Th from '@/components/table/Th.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {byDate, byValue, useSortable, type SortKey} from '@/composables/useSortable'
import type {FeedUse} from '@/api/feedToken'
import FeedUseRow from './FeedUseRow.vue'
import {lastFetched} from './feedUse'

/**
 * The subscriptions of one station, searchable by name and sortable by every column. Newest first
 * to begin with: a subscription set up today is the one somebody is likely asking about.
 */
const props = defineProps<{
  uses: FeedUse[]
}>()

const {t} = useI18n()

const search = ref('')

const matching = computed(() => {
  const needle = search.value.trim().toLowerCase()
  if (!needle) return props.uses
  return props.uses.filter(use => (use.identity?.name ?? '').toLowerCase().includes(needle))
})

const {sorted, sortKey, direction, toggle} = useSortable<FeedUse, SortKey>({
  items: matching,
  initialKey: 'createdAt',
  initialDirection: 'desc',
  comparators: {
    name: byValue(use => use.identity?.name ?? ''),
    createdAt: byDate(use => use.createdAt),
    ical: byDate(use => use.icalPolledAt, {nulls: 'last'}),
    notifications: byDate(use => use.notificationPolledAt, {nulls: 'last'}),
    lastFetched: byDate(use => lastFetched(use), {nulls: 'last'}),
  },
})
</script>

<template>
  <div class="space-y-3">
    <SearchInput v-model="search" :placeholder="t('stationFeeds.searchPlaceholder')"/>

    <MutedText v-if="sorted.length === 0" tag="p" size="sm">{{ t('stationFeeds.noneFound') }}</MutedText>

    <DataTable v-else>
      <template #head>
        <SortableHeader :label="t('stationFeeds.column.member')" sort-key="name" :active-key="sortKey" :direction="direction" @sort="toggle"/>
        <SortableHeader :label="t('stationFeeds.column.since')" sort-key="createdAt" :active-key="sortKey" :direction="direction" @sort="toggle"/>
        <SortableHeader :label="t('stationFeeds.column.calendar')" sort-key="ical" :active-key="sortKey" :direction="direction" @sort="toggle"/>
        <SortableHeader :label="t('stationFeeds.column.notifications')" sort-key="notifications" :active-key="sortKey" :direction="direction" @sort="toggle"/>
        <Th align="right">{{ t('stationFeeds.column.state') }}</Th>
      </template>

      <FeedUseRow v-for="use in sorted" :key="use.memberId" :use="use"/>
    </DataTable>
  </div>
</template>
