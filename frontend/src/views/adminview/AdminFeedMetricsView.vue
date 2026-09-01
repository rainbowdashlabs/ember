/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {use} from 'echarts/core'
import {CanvasRenderer} from 'echarts/renderers'
import {BarChart, LineChart} from 'echarts/charts'
import {DataZoomComponent, GridComponent, LegendComponent, TooltipComponent} from 'echarts/components'
import HelpCenterHint from '@/components/help/HelpCenterHint.vue'
import {feedMetrics} from '@/api'
import type {FeedMetricDaily, FeedUserAgentStat} from '@/api/feedMetrics'
import FeedMetricsSummary from './adminfeedmetricsview/FeedMetricsSummary.vue'
import FeedMetricsCharts from './adminfeedmetricsview/FeedMetricsCharts.vue'
import FeedMetricsTables from './adminfeedmetricsview/FeedMetricsTables.vue'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {bottomLegend, cartesianGrid} from '@/util/chartLayout'
import {darkThemeActive as isDark} from '@/util/themeState'

use([CanvasRenderer, BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent])

const {t} = useI18n()

const daily = ref<FeedMetricDaily[]>([])
const userAgents = ref<FeedUserAgentStat[]>([])
const totalRequests = ref(0)

const textColor = computed(() => (isDark.value ? '#ccc' : '#333'))

const {loading} = useAsyncLoader(async () => {
  const [d, ua] = await Promise.all([
    feedMetrics.getDailyMetrics(30),
    feedMetrics.getUserAgents(50),
  ])
  daily.value = d
  userAgents.value = ua.userAgents
  totalRequests.value = ua.totalRequests
})

/** Distinct days sorted ascending, used as the X axis for every chart. */
const days = computed(() => {
  const set = new Set<string>()
  for (const row of daily.value) set.add(row.day)
  return [...set].sort()
})

/** Per-day request count, broken out by feed type. Sums counts across status codes -
 *  totals are what answer "how loaded is the feed today". */
const requestsByType = computed(() => {
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

/** Status-code breakdown across the window - sparkline-style. */
const statusBreakdown = computed(() => {
  const map = new Map<number, number>()
  for (const row of daily.value) map.set(row.status, (map.get(row.status) ?? 0) + row.count)
  return [...map.entries()].sort(([a], [b]) => a - b)
})

const requestsChartOption = computed(() => ({
  tooltip: {trigger: 'axis'},
  legend: bottomLegend(textColor.value, ['iCal', 'RSS', 'Atom']),
  grid: cartesianGrid({legend: true, axisName: true}),
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
  grid: cartesianGrid({axisName: true}),
  xAxis: {
    type: 'category',
    data: ['< 50ms', '50–200ms', '200ms–1s', '1–5s', '≥ 5s'],
    axisLabel: {color: textColor.value},
  },
  yAxis: {type: 'value', name: t('feedMetrics.requests'), nameTextStyle: {color: textColor.value}, axisLabel: {color: textColor.value}},
  series: [
    {
      type: 'bar',
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
  grid: cartesianGrid({axisName: true}),
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
  <ViewContent :title="t('pages.admin-feed-metrics.title')" :subtitle="t('pages.admin-feed-metrics.subtitle')">
    <div class="space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <MutedText tag="p" size="sm">{{ t('feedMetrics.subtitle') }}</MutedText>
        </div>
        <HelpCenterHint :to="{name: 'help-admin-feed-metrics'}">
          {{ t('feedMetrics.help') }}
        </HelpCenterHint>
      </div>

      <Spinner v-if="loading"/>
      <template v-else>
        <FeedMetricsSummary
            :total-requests="totalRequests"
            :total-rendered="totalRendered"
            :total-cache-hits="totalCacheHits"
            :avg-duration-ms="avgDurationMs"/>
        <FeedMetricsCharts
            :has-data="days.length > 0"
            :requests-chart-option="requestsChartOption"
            :histogram-chart-option="histogramChartOption"
            :volume-chart-option="volumeChartOption"/>
        <FeedMetricsTables
            :status-breakdown="statusBreakdown"
            :user-agents="userAgents"/>
      </template>
    </div>
  </ViewContent>
</template>
