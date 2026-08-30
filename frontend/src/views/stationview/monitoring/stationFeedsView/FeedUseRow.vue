/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MemberName from '@/components/avatar/MemberName.vue'
import TRow from '@/components/table/TRow.vue'
import Td from '@/components/table/Td.vue'
import {formatDate, formatRelative} from '@/util/format'
import type {FeedUse} from '@/api/feedToken'
import {usedRecently} from './feedUse'

/**
 * One member's subscription. A feed nobody fetches is the interesting row, so "never" is spelled
 * out rather than left as an empty cell.
 */
const props = defineProps<{
  use: FeedUse
}>()

const {t} = useI18n()

const dormant = computed(() => !usedRecently(props.use))

function fetched(stamp?: string | null): string {
  return stamp ? formatRelative(stamp) : t('stationFeeds.never')
}
</script>

<template>
  <TRow data-testid="feed-use-row">
    <Td>
      <MemberName :identity="use.identity"/>
    </Td>
    <Td muted>{{ formatDate(use.createdAt) }}</Td>
    <Td :muted="!use.icalPolledAt">{{ fetched(use.icalPolledAt) }}</Td>
    <Td :muted="!use.notificationPolledAt">{{ fetched(use.notificationPolledAt) }}</Td>
    <Td align="right">
      <span v-if="dormant" class="text-(--text-muted)">{{ t('stationFeeds.dormant') }}</span>
      <span v-else class="text-success">{{ t('stationFeeds.inUse') }}</span>
    </Td>
  </TRow>
</template>
