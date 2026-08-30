/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import HelpCenterHint from '@/components/help/HelpCenterHint.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import StatTile from '@/components/statistic/StatTile.vue'
import {feedToken} from '@/api'
import type {FeedUse} from '@/api/feedToken'
import {useConfigPanel} from '@/composables/useConfigPanel'
import FeedUseTable from './stationFeedsView/FeedUseTable.vue'
import {usedRecently} from './stationFeedsView/feedUse'

/**
 * Who at this station subscribes to the calendar or the notification feed, and whether anything
 * still fetches it. The subscription's own key is never part of the answer.
 */
const {t} = useI18n()

const {config: uses, loading} = useConfigPanel<FeedUse[]>({
  initial: [],
  fetch: () => feedToken.getStationFeedUse(),
  formatError: () => '',
})

const active = computed(() => uses.value.filter(use => usedRecently(use)).length)
</script>

<template>
  <ViewContent :title="t('pages.station-feeds.title')" :subtitle="t('pages.station-feeds.subtitle')">
    <div class="space-y-4">
      <div class="flex flex-wrap items-center justify-end gap-3">
        <HelpCenterHint :to="{name: 'help-station-feeds'}">
          {{ t('stationFeeds.help') }}
        </HelpCenterHint>
      </div>

      <Spinner v-if="loading"/>
      <template v-else>
        <div class="grid grid-cols-2 gap-3">
          <StatTile :value="uses.length" :label="t('stationFeeds.total')"/>
          <StatTile :value="active" :label="t('stationFeeds.activeLastWeek')" color="success"/>
        </div>

        <FeedUseTable :uses="uses"/>
      </template>
    </div>
  </ViewContent>
</template>
