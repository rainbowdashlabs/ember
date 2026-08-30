/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import {insights} from '@/api'
import type {HourlyTotal, PageDetailResponse, PageLeaderboardEntry} from '@/api/insights'
import InsightsWindowSelector from '@/views/stationview/monitoring/stationInsightsView/InsightsWindowSelector.vue'
import InsightsHeader from '@/views/stationview/monitoring/stationInsightsView/InsightsHeader.vue'
import InsightsTotalsGrid from '@/views/stationview/monitoring/stationInsightsView/InsightsTotalsGrid.vue'
import LeaderboardPanel from '@/views/stationview/monitoring/stationInsightsView/LeaderboardPanel.vue'
import DetailPanel from '@/views/stationview/monitoring/stationInsightsView/DetailPanel.vue'

const {t} = useI18n()

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
  <ViewContent
      :title="t('pages.station-insights.title')"
      :subtitle="t('pages.station-insights.subtitle')"
  >
    <div class="space-y-4">
      <InsightsHeader/>
      <InsightsWindowSelector
          v-model:window-hours="windowHours"
          v-model:include-bots="includeBots"/>
      <InsightsTotalsGrid
          :total-count="leaderboard.length"
          :total-hits="totalHits"
          :total-pages="totalPages"/>
      <LeaderboardPanel
          :include-bots="includeBots"
          :loading="loadingLeaderboard"
          :rows="leaderboard"
          :selected-page-id="selectedPageId"
          @select="loadDetail"/>
      <DetailPanel
          :detail="detail"
          :hourly-series="hourlySeries"
          :loading-detail="loadingDetail"
          :selected-page="selectedPage"/>
    </div>
  </ViewContent>
</template>
