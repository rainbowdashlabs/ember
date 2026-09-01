/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {use} from 'echarts/core'
import {CanvasRenderer} from 'echarts/renderers'
import {BarChart, LineChart, PieChart} from 'echarts/charts'
import {GridComponent, LegendComponent, TitleComponent, TooltipComponent} from 'echarts/components'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import client from '@/api/client'
import {useSession} from '@/composables/useSession'
import {useConfigPanel} from '@/composables/useConfigPanel'
import SummaryCards from '@/views/dashboardview/statisticsview/SummaryCards.vue'
import ChartGrid from '@/views/dashboardview/statisticsview/ChartGrid.vue'
import type {StatsData} from '@/views/dashboardview/statisticsview/statsData'
import {bottomLegend, cartesianGrid, DONUT_CENTER, DONUT_RADIUS, chartTitle} from '@/util/chartLayout'
import {darkThemeActive as isDark} from '@/util/themeState'

use([CanvasRenderer, BarChart, PieChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const {t} = useI18n()
const {loaded} = useSession()

onMounted(() => {
  if (loaded.value) loadStats()
})

const textColor = computed(() => isDark.value ? '#e0e0e0' : '#333333')
const mutedColor = computed(() => isDark.value ? '#9ca3af' : '#666666')


const {config: stats, loading, error, reload: loadStats} = useConfigPanel<StatsData | null>({
  initial: null,
  fetch: async () => (await client.get<StatsData>('/statistics')).data,
  immediate: false,
})

const groupPieOption = computed(() => {
  if (!stats.value) return {}
  const entries = Object.entries(stats.value.groupCounts)
  return {
    title: chartTitle(t('statistics.groupDistribution'), textColor.value),
    tooltip: {trigger: 'item', formatter: '{b}: {c} ({d}%)'},
    legend: bottomLegend(mutedColor.value),
    series: [{
      type: 'pie',
      radius: DONUT_RADIUS,
      center: DONUT_CENTER,
      data: entries.map(([name, value]) => ({name, value})),
      label: {color: mutedColor.value},
      emphasis: {itemStyle: {shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.2)'}},
    }],
    color: ['#FF6421', '#73CEFF', '#00C507', '#ffdd1b', '#3694FF', '#C71100'],
  }
})

const attendanceBarOption = computed(() => {
  if (!stats.value || stats.value.attendanceByMonth.length === 0) return {}
  const months = stats.value.attendanceByMonth.map(a => a.month)
  return {
    title: chartTitle(t('statistics.attendanceOverTime'), textColor.value),
    tooltip: {trigger: 'axis'},
    legend: bottomLegend(mutedColor.value),
    grid: cartesianGrid({legend: true, title: true, left: 50}),
    xAxis: {type: 'category', data: months, axisLabel: {color: mutedColor.value}, axisLine: {lineStyle: {color: mutedColor.value}}},
    yAxis: {type: 'value', axisLabel: {color: mutedColor.value}, axisLine: {lineStyle: {color: mutedColor.value}}, splitLine: {lineStyle: {color: isDark.value ? '#333' : '#e0e0e0'}}},
    series: [
      {
        name: t('statistics.present'),
        type: 'bar',
        stack: 'total',
        data: stats.value.attendanceByMonth.map(a => a.present),
        color: '#00C507'
      },
      {
        name: t('statistics.absent'),
        type: 'bar',
        stack: 'total',
        data: stats.value.attendanceByMonth.map(a => a.absent),
        color: '#ec2929'
      },
      {
        name: t('statistics.declined'),
        type: 'bar',
        stack: 'total',
        data: stats.value.attendanceByMonth.map(a => a.declined),
        color: '#3694FF'
      },
    ],
  }
})

const sessionsLineOption = computed(() => {
  if (!stats.value || stats.value.attendanceByMonth.length === 0) return {}
  const months = stats.value.attendanceByMonth.map(a => a.month)
  return {
    title: chartTitle(t('statistics.sessionsPerMonth'), textColor.value),
    tooltip: {trigger: 'axis'},
    grid: cartesianGrid({title: true, left: 50}),
    xAxis: {type: 'category', data: months, axisLabel: {color: mutedColor.value}, axisLine: {lineStyle: {color: mutedColor.value}}},
    yAxis: {type: 'value', minInterval: 1, axisLabel: {color: mutedColor.value}, axisLine: {lineStyle: {color: mutedColor.value}}, splitLine: {lineStyle: {color: isDark.value ? '#333' : '#e0e0e0'}}},
    series: [{
      type: 'line',
      data: stats.value.attendanceByMonth.map(a => a.sessions),
      smooth: true,
      areaStyle: {opacity: 0.15},
      color: '#FF6421',
    }],
  }
})

const inventoryBarOption = computed(() => {
  if (!stats.value || stats.value.inventoryStatus.length === 0) return {}
  return {
    title: chartTitle(t('statistics.inventoryStatus'), textColor.value),
    tooltip: {trigger: 'axis'},
    legend: bottomLegend(mutedColor.value),
    grid: cartesianGrid({legend: true, title: true, left: 100}),
    yAxis: {type: 'category', data: stats.value.inventoryStatus.map(i => i.name), axisLabel: {color: mutedColor.value}, axisLine: {lineStyle: {color: mutedColor.value}}},
    xAxis: {type: 'value', axisLabel: {color: mutedColor.value}, axisLine: {lineStyle: {color: mutedColor.value}}, splitLine: {lineStyle: {color: isDark.value ? '#333' : '#e0e0e0'}}},
    series: [
      {
        name: t('statistics.assigned'),
        type: 'bar',
        stack: 'inv',
        data: stats.value.inventoryStatus.map(i => i.assigned),
        color: '#00C507'
      },
      {
        name: t('statistics.unassigned'),
        type: 'bar',
        stack: 'inv',
        data: stats.value.inventoryStatus.map(i => i.total - i.assigned - i.lost),
        color: '#CFCFCF'
      },
      {
        name: t('statistics.lost'),
        type: 'bar',
        stack: 'inv',
        data: stats.value.inventoryStatus.map(i => i.lost),
        color: '#ec2929'
      },
    ],
  }
})

const registrationBarOption = computed(() => {
  if (!stats.value || stats.value.eventRegistrations.length === 0) return {}
  return {
    title: chartTitle(t('statistics.eventRegistrations'), textColor.value),
    tooltip: {trigger: 'axis'},
    legend: bottomLegend(mutedColor.value),
    grid: cartesianGrid({legend: true, title: true, left: 150}),
    yAxis: {type: 'category', data: stats.value.eventRegistrations.map(e => e.name), axisLabel: {color: mutedColor.value}, axisLine: {lineStyle: {color: mutedColor.value}}},
    xAxis: {type: 'value', minInterval: 1, axisLabel: {color: mutedColor.value}, axisLine: {lineStyle: {color: mutedColor.value}}, splitLine: {lineStyle: {color: isDark.value ? '#333' : '#e0e0e0'}}},
    series: [
      {
        name: t('statistics.accepted'),
        type: 'bar',
        stack: 'reg',
        data: stats.value.eventRegistrations.map(e => e.accepted),
        color: '#00C507'
      },
      {
        name: t('statistics.pending'),
        type: 'bar',
        stack: 'reg',
        data: stats.value.eventRegistrations.map(e => e.pending),
        color: '#ffdd1b'
      },
      {
        name: t('statistics.declinedReg'),
        type: 'bar',
        stack: 'reg',
        data: stats.value.eventRegistrations.map(e => e.declined),
        color: '#ec2929'
      },
    ],
  }
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadStats()
})
</script>

<template>
  <ViewContent :title="t('pages.dashboard-statistics.title')" :subtitle="t('pages.dashboard-statistics.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && stats">
        <SummaryCards :stats="stats"/>
        <ChartGrid
            :group-pie-option="groupPieOption"
            :sessions-line-option="sessionsLineOption"
            :attendance-bar-option="attendanceBarOption"
            :inventory-bar-option="inventoryBarOption"
            :registration-bar-option="registrationBarOption"
            :show-registrations="stats.eventRegistrations.length > 0"
        />
      </template>
    </div>
  </ViewContent>
</template>
