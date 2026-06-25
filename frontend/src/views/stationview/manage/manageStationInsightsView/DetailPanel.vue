/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import InsightsHourlyChart from '@/views/stationview/manage/manageStationInsightsView/InsightsHourlyChart.vue'
import DimensionBreakdown from '@/views/stationview/manage/manageStationInsightsView/DimensionBreakdown.vue'
import type {HourlyTotal, PageDetailResponse, PageLeaderboardEntry} from '@/api/insights'

defineProps<{
  selectedPage: PageLeaderboardEntry | null
  loadingDetail: boolean
  detail: PageDetailResponse | null
  hourlySeries: HourlyTotal[]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer v-if="selectedPage || loadingDetail" class="space-y-3">
    <SectionHeader v-if="selectedPage">
      {{ t('insights.detail.title', {page: selectedPage.title}) }}
    </SectionHeader>
    <Spinner v-if="loadingDetail"/>
    <template v-else-if="detail">
      <InsightsHourlyChart :rows="hourlySeries"/>
      <MutedText v-if="hourlySeries.length === 0" tag="div" size="sm">{{ t('insights.noData') }}</MutedText>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
        <div class="space-y-2">
          <SectionHeader>{{ t('insights.detail.countries') }}</SectionHeader>
          <DimensionBreakdown :rows="detail.countries"/>
        </div>
        <div class="space-y-2">
          <SectionHeader>{{ t('insights.detail.referrers') }}</SectionHeader>
          <DimensionBreakdown :rows="detail.referrers"/>
        </div>
      </div>
    </template>
  </NeutralContainer>
</template>
