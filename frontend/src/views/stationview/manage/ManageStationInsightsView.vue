/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import HelpCenterHint from '@/components/help/HelpCenterHint.vue'
import {insights} from '@/api'
import type {HourlyTotal, PageDetailResponse, PageLeaderboardEntry} from '@/api/insights'
import InsightsWindowSelector from '@/views/stationview/manage/manageStationInsightsView/InsightsWindowSelector.vue'
import InsightsHourlyChart from '@/views/stationview/manage/manageStationInsightsView/InsightsHourlyChart.vue'
import PageLeaderboardTable from '@/views/stationview/manage/manageStationInsightsView/PageLeaderboardTable.vue'
import DimensionBreakdown from '@/views/stationview/manage/manageStationInsightsView/DimensionBreakdown.vue'

const {t, n} = useI18n()

const windowHours = ref(168)
const includeBots = ref(false)
const leaderboard = ref<PageLeaderboardEntry[]>([])
const detail = ref<PageDetailResponse | null>(null)
const selectedPageId = ref<number | null>(null)
const loadingLeaderboard = ref(false)
const loadingDetail = ref(false)

function currentWindow(): {from: string; to: string} {
  const to = new Date()
  const from = new Date(to.getTime() - windowHours.value * 3600_000)
  return {from: from.toISOString(), to: to.toISOString()}
}

async function loadLeaderboard() {
  loadingLeaderboard.value = true
  try {
    const res = await insights.getLeaderboard(currentWindow())
    leaderboard.value = res.rows
    if (selectedPageId.value !== null && !res.rows.some(r => r.pageId === selectedPageId.value)) {
      selectedPageId.value = null
      detail.value = null
    }
  } finally {
    loadingLeaderboard.value = false
  }
}

async function loadDetail(pageId: number) {
  loadingDetail.value = true
  try {
    detail.value = await insights.getPageDetail(pageId, currentWindow())
    selectedPageId.value = pageId
  } finally {
    loadingDetail.value = false
  }
}

const totalHits = computed(() => leaderboard.value.reduce(
    (sum, r) => sum + r.hits + (includeBots.value ? r.botHits : 0), 0))

const totalPages = computed(() => leaderboard.value.filter(
    r => r.hits + r.botHits > 0).length)

const hourlySeries = computed<HourlyTotal[]>(() => {
  if (!detail.value) return []
  return includeBots.value ? detail.value.hourlyWithBots : detail.value.hourly
})

const selectedPage = computed(() => leaderboard.value.find(r => r.pageId === selectedPageId.value) ?? null)

onMounted(loadLeaderboard)
watch(windowHours, async () => {
  await loadLeaderboard()
  if (selectedPageId.value !== null) await loadDetail(selectedPageId.value)
})
</script>

<template>
  <ViewContent>
    <div class="space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <PageHeader>{{ t('insights.title') }}</PageHeader>
          <MutedText tag="p" size="sm">{{ t('insights.subtitle') }}</MutedText>
        </div>
        <HelpCenterHint :to="{name: 'help-station-insights'}">
          {{ t('insights.help') }}
        </HelpCenterHint>
      </div>

      <InsightsWindowSelector
          v-model:window-hours="windowHours"
          v-model:include-bots="includeBots"/>

      <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
        <NeutralContainer class="space-y-1">
          <MutedText tag="div" size="sm">{{ t('insights.totals.hits') }}</MutedText>
          <div class="text-2xl font-semibold">{{ n(totalHits) }}</div>
        </NeutralContainer>
        <NeutralContainer class="space-y-1">
          <MutedText tag="div" size="sm">{{ t('insights.totals.pagesWithHits') }}</MutedText>
          <div class="text-2xl font-semibold">{{ n(totalPages) }}</div>
        </NeutralContainer>
        <NeutralContainer class="space-y-1">
          <MutedText tag="div" size="sm">{{ t('insights.totals.publicPages') }}</MutedText>
          <div class="text-2xl font-semibold">{{ n(leaderboard.length) }}</div>
        </NeutralContainer>
      </div>

      <NeutralContainer class="space-y-2">
        <SectionHeader>{{ t('insights.leaderboard.title') }}</SectionHeader>
        <MutedText tag="p" size="sm">{{ t('insights.leaderboard.subtitle') }}</MutedText>
        <Spinner v-if="loadingLeaderboard"/>
        <PageLeaderboardTable
            v-else
            :include-bots="includeBots"
            :rows="leaderboard"
            :selected-page-id="selectedPageId"
            @select="loadDetail"/>
      </NeutralContainer>

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
    </div>
  </ViewContent>
</template>
