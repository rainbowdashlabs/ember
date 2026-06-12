/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import VChart from 'vue-echarts'
import {use} from 'echarts/core'
import {CanvasRenderer} from 'echarts/renderers'
import {BarChart, LineChart} from 'echarts/charts'
import {DataZoomComponent, GridComponent, LegendComponent, TooltipComponent} from 'echarts/components'
import HelpCenterHint from '@/components/help/HelpCenterHint.vue'
import {feedMetrics} from '@/api'
import type {FeedMetricDaily, FeedUserAgentStat} from '@/api/feedMetrics'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent])

const {t, n} = useI18n()

const loading = ref(true)
const daily = ref<FeedMetricDaily[]>([])
const userAgents = ref<FeedUserAgentStat[]>([])
const totalRequests = ref(0)

const isDark = computed(() => typeof document !== 'undefined' && document.documentElement.classList.contains('dark'))
const textColor = computed(() => (isDark.value ? '#ccc' : '#333'))

async function loadData() {
  loading.value = true
  try {
    const [d, ua] = await Promise.all([
      feedMetrics.getDailyMetrics(30),
      feedMetrics.getUserAgents(50),
    ])
    daily.value = d
    userAgents.value = ua.userAgents
    totalRequests.value = ua.totalRequests
  } finally {
    loading.value = false
  }
}

onMounted(loadData)

// -- derived series --

/** Distinct days sorted ascending, used as the X axis for every chart. */
const days = computed(() => {
  const set = new Set<string>()
  for (const row of daily.value) set.add(row.day)
  return [...set].sort()
})

/** Per-day request count, broken out by feed type. */
const requestsByType = computed(() => {
  // Sum counts across status codes — totals are what answer "how loaded is the feed today".
  const make = (type: string) => days.value.map(day => {
    return daily.value
        .filter(r => r.day === day && r.type === type)
        .reduce((sum, r) => sum + r.count, 0)
  })
  return {ics: make('ics'), rss: make('rss'), atom: make('atom')}
})

/** Per-day total requests across every type, for the histogram view. */
const dailyTotals = computed(() => days.value.map(day =>
    daily.value.filter(r => r.day === day).reduce((sum, r) => sum + r.count, 0),
))

/** Aggregate response-time histogram across the entire window. The five bins each become
 *  a single bar so a single glance tells the team whether the feed is mostly fast. */
const histogramTotals = computed(() => {
  const sum = (key: keyof FeedMetricDaily) =>
      daily.value.reduce((acc, r) => acc + (r[key] as number), 0)
  return {
    lt50: sum('bucketLt50'),
    lt200: sum('bucketLt200'),
    lt1000: sum('bucketLt1000'),
    lt5000: sum('bucketLt5000'),
    gte5000: sum('bucketGte5000'),
  }
})

/** Average ms per render across all rows. Useful summary number. */
const avgDurationMs = computed(() => {
  const tot = daily.value.reduce(
      (acc, r) => ({count: acc.count + r.count, dur: acc.dur + r.totalDurationMs}),
      {count: 0, dur: 0},
  )
  return tot.count > 0 ? tot.dur / tot.count : 0
})

/** Total successful renders (status 200). 304s are excluded so the user sees real workload. */
const totalRendered = computed(() =>
    daily.value.filter(r => r.status === 200).reduce((sum, r) => sum + r.count, 0),
)

const totalCacheHits = computed(() =>
    daily.value.filter(r => r.status === 304).reduce((sum, r) => sum + r.count, 0),
)

const totalErrors = computed(() =>
    daily.value.filter(r => r.status >= 500).reduce((sum, r) => sum + r.count, 0),
)

/** Status-code breakdown across the window — sparkline-style. */
const statusBreakdown = computed(() => {
  const map = new Map<number, number>()
  for (const row of daily.value) map.set(row.status, (map.get(row.status) ?? 0) + row.count)
  return [...map.entries()].sort(([a], [b]) => a - b)
})

// -- chart options --

const requestsChartOption = computed(() => ({
  tooltip: {trigger: 'axis'},
  legend: {data: ['iCal', 'RSS', 'Atom'], textStyle: {color: textColor.value}},
  grid: {left: 60, right: 20, bottom: 40},
  xAxis: {type: 'category', data: days.value, axisLabel: {color: textColor.value}},
  yAxis: {type: 'value', name: t('feedMetrics.requests'), nameTextStyle: {color: textColor.value}, axisLabel: {color: textColor.value}},
  series: [
    {name: 'iCal', type: 'line', smooth: true, data: requestsByType.value.ics, itemStyle: {color: '#FF6421'}},
    {name: 'RSS', type: 'line', smooth: true, data: requestsByType.value.rss, itemStyle: {color: '#3694FF'}},
    {name: 'Atom', type: 'line', smooth: true, data: requestsByType.value.atom, itemStyle: {color: '#00C507'}},
  ],
}))

const histogramChartOption = computed(() => ({
  tooltip: {trigger: 'axis'},
  grid: {left: 60, right: 20, bottom: 40},
  xAxis: {
    type: 'category',
    data: ['< 50ms', '50–200ms', '200ms–1s', '1–5s', '≥ 5s'],
    axisLabel: {color: textColor.value},
  },
  yAxis: {type: 'value', name: t('feedMetrics.requests'), nameTextStyle: {color: textColor.value}, axisLabel: {color: textColor.value}},
  series: [
    {
      type: 'bar',
      // Healthy feeds skew green to yellow to red as duration grows; the bar colours
      // pre-bake the visual story so a glance tells you if anything's slow.
      data: [
        {value: histogramTotals.value.lt50, itemStyle: {color: '#00C507'}},
        {value: histogramTotals.value.lt200, itemStyle: {color: '#73CEFF'}},
        {value: histogramTotals.value.lt1000, itemStyle: {color: '#ffdd1b'}},
        {value: histogramTotals.value.lt5000, itemStyle: {color: '#FF6421'}},
        {value: histogramTotals.value.gte5000, itemStyle: {color: '#ec2929'}},
      ],
    },
  ],
}))

const volumeChartOption = computed(() => ({
  tooltip: {trigger: 'axis'},
  grid: {left: 60, right: 20, bottom: 40},
  xAxis: {type: 'category', data: days.value, axisLabel: {color: textColor.value}},
  yAxis: {type: 'value', name: t('feedMetrics.requests'), nameTextStyle: {color: textColor.value}, axisLabel: {color: textColor.value}},
  series: [{
    type: 'bar',
    data: dailyTotals.value,
    itemStyle: {color: '#3694FF'},
  }],
}))
</script>

<template>
  <ViewContent>
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <PageHeader>{{ t('feedMetrics.title') }}</PageHeader>
        <MutedText tag="p" size="sm">{{ t('feedMetrics.subtitle') }}</MutedText>
      </div>
      <HelpCenterHint :to="{name: 'help-admin-feed-metrics'}">
        {{ t('feedMetrics.help') }}
      </HelpCenterHint>
    </div>

    <Spinner v-if="loading"/>
    <template v-else>
      <!-- Summary numbers -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
        <NeutralContainer class="space-y-1">
          <MutedText tag="div" size="sm">{{ t('feedMetrics.totalRequests') }}</MutedText>
          <div class="text-2xl font-semibold">{{ n(totalRequests) }}</div>
        </NeutralContainer>
        <NeutralContainer class="space-y-1">
          <MutedText tag="div" size="sm">{{ t('feedMetrics.totalRendered') }}</MutedText>
          <div class="text-2xl font-semibold">{{ n(totalRendered) }}</div>
        </NeutralContainer>
        <NeutralContainer class="space-y-1">
          <MutedText tag="div" size="sm">{{ t('feedMetrics.cacheHits') }}</MutedText>
          <div class="text-2xl font-semibold">{{ n(totalCacheHits) }}</div>
        </NeutralContainer>
        <NeutralContainer class="space-y-1">
          <MutedText tag="div" size="sm">{{ t('feedMetrics.avgDuration') }}</MutedText>
          <div class="text-2xl font-semibold">{{ Math.round(avgDurationMs) }} ms</div>
        </NeutralContainer>
      </div>

      <!-- Requests per day, broken out by feed type -->
      <NeutralContainer class="space-y-2">
        <SectionHeader>{{ t('feedMetrics.requestsByType') }}</SectionHeader>
        <VChart v-if="days.length > 0" :option="requestsChartOption" autoresize style="height: 280px"/>
        <MutedText v-else tag="div" size="sm">{{ t('feedMetrics.noData') }}</MutedText>
      </NeutralContainer>

      <!-- Latency histogram across the window -->
      <NeutralContainer class="space-y-2">
        <SectionHeader>{{ t('feedMetrics.latencyHistogram') }}</SectionHeader>
        <MutedText tag="p" size="sm">{{ t('feedMetrics.latencyHistogramHint') }}</MutedText>
        <VChart v-if="days.length > 0" :option="histogramChartOption" autoresize style="height: 280px"/>
        <MutedText v-else tag="div" size="sm">{{ t('feedMetrics.noData') }}</MutedText>
      </NeutralContainer>

      <!-- Daily total volume -->
      <NeutralContainer class="space-y-2">
        <SectionHeader>{{ t('feedMetrics.dailyVolume') }}</SectionHeader>
        <VChart v-if="days.length > 0" :option="volumeChartOption" autoresize style="height: 240px"/>
        <MutedText v-else tag="div" size="sm">{{ t('feedMetrics.noData') }}</MutedText>
      </NeutralContainer>

      <!-- Status breakdown table -->
      <NeutralContainer class="space-y-2">
        <SectionHeader>{{ t('feedMetrics.statusBreakdown') }}</SectionHeader>
        <table v-if="statusBreakdown.length > 0" class="w-full text-sm">
          <thead>
          <tr class="text-left text-(--text-muted)">
            <th class="py-1 pr-3 font-medium">{{ t('feedMetrics.status') }}</th>
            <th class="py-1 pr-3 font-medium">{{ t('feedMetrics.count') }}</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="[status, count] in statusBreakdown" :key="status" class="border-t border-(--border)">
            <td class="py-1 pr-3 font-mono">{{ status }}</td>
            <td class="py-1 pr-3">{{ n(count) }}</td>
          </tr>
          </tbody>
        </table>
        <MutedText v-else tag="div" size="sm">{{ t('feedMetrics.noData') }}</MutedText>
      </NeutralContainer>

      <!-- User-agent leaderboard -->
      <NeutralContainer class="space-y-2">
        <SectionHeader>{{ t('feedMetrics.topUserAgents') }}</SectionHeader>
        <MutedText tag="p" size="sm">{{ t('feedMetrics.topUserAgentsHint') }}</MutedText>
        <table v-if="userAgents.length > 0" class="w-full text-sm">
          <thead>
          <tr class="text-left text-(--text-muted)">
            <th class="py-1 pr-3 font-medium">{{ t('feedMetrics.userAgent') }}</th>
            <th class="py-1 pr-3 font-medium">{{ t('feedMetrics.requests') }}</th>
            <th class="py-1 pr-3 font-medium">{{ t('feedMetrics.lastSeen') }}</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="ua in userAgents" :key="ua.uaHash" class="border-t border-(--border) align-top">
            <td class="py-1 pr-3 font-mono break-all">{{ ua.uaString }}</td>
            <td class="py-1 pr-3 whitespace-nowrap">{{ n(ua.requestCount) }}</td>
            <td class="py-1 pr-3 whitespace-nowrap">{{ new Date(ua.lastSeen).toLocaleString() }}</td>
          </tr>
          </tbody>
        </table>
        <MutedText v-else tag="div" size="sm">{{ t('feedMetrics.noData') }}</MutedText>
      </NeutralContainer>
    </template>
  </ViewContent>
</template>
