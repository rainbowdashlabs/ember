/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, onUnmounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {use} from 'echarts/core'
import {CanvasRenderer} from 'echarts/renderers'
import {BarChart, LineChart, PieChart} from 'echarts/charts'
import {GridComponent, LegendComponent, TitleComponent, TooltipComponent} from 'echarts/components'
import VChart from 'vue-echarts'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import StatValue from '@/components/typography/StatValue.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import client from '@/api/client'
import {useSession} from '@/composables/useSession'

use([CanvasRenderer, BarChart, PieChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const {t} = useI18n()
const {loaded} = useSession()

const isDark = ref(document.documentElement.classList.contains('dark'))
let observer: MutationObserver | null = null

onMounted(() => {
  observer = new MutationObserver(() => {
    isDark.value = document.documentElement.classList.contains('dark')
  })
  observer.observe(document.documentElement, {attributes: true, attributeFilter: ['class']})
  if (loaded.value) loadStats()
})

onUnmounted(() => {
  observer?.disconnect()
})

const textColor = computed(() => isDark.value ? '#e0e0e0' : '#333333')
const mutedColor = computed(() => isDark.value ? '#9ca3af' : '#666666')

interface StatsData {
  memberCount: number
  groupCounts: Record<string, number>
  attendanceByMonth: Array<{ month: string; sessions: number; present: number; absent: number; declined: number }>
  inventoryStatus: Array<{ name: string; total: number; assigned: number; lost: number }>
  eventRegistrations: Array<{ name: string; accepted: number; pending: number; declined: number }>
  roleCounts: Record<string, number>
}

const stats = ref<StatsData | null>(null)
const loading = ref(true)
const error = ref('')

async function loadStats() {
  loading.value = true
  error.value = ''
  try {
    const res = await client.get<StatsData>('/statistics')
    stats.value = res.data
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

const groupPieOption = computed(() => {
  if (!stats.value) return {}
  const entries = Object.entries(stats.value.groupCounts)
  return {
    title: {text: t('statistics.groupDistribution'), left: 'center', textStyle: {fontSize: 14, color: textColor.value}},
    tooltip: {trigger: 'item', formatter: '{b}: {c} ({d}%)'},
    legend: {bottom: 0, textStyle: {color: mutedColor.value}},
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
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
    title: {text: t('statistics.attendanceOverTime'), left: 'center', textStyle: {fontSize: 14, color: textColor.value}},
    tooltip: {trigger: 'axis'},
    legend: {bottom: 0, textStyle: {color: mutedColor.value}},
    grid: {left: 50, right: 20, top: 40, bottom: 40},
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
    title: {text: t('statistics.sessionsPerMonth'), left: 'center', textStyle: {fontSize: 14, color: textColor.value}},
    tooltip: {trigger: 'axis'},
    grid: {left: 50, right: 20, top: 40, bottom: 20},
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
    title: {text: t('statistics.inventoryStatus'), left: 'center', textStyle: {fontSize: 14, color: textColor.value}},
    tooltip: {trigger: 'axis'},
    legend: {bottom: 0, textStyle: {color: mutedColor.value}},
    grid: {left: 100, right: 20, top: 40, bottom: 40},
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
    title: {text: t('statistics.eventRegistrations'), left: 'center', textStyle: {fontSize: 14, color: textColor.value}},
    tooltip: {trigger: 'axis'},
    legend: {bottom: 0, textStyle: {color: mutedColor.value}},
    grid: {left: 150, right: 20, top: 40, bottom: 40},
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
  <ViewContent>
    <div class="space-y-6">
      <SectionHeader>{{ t('statistics.title') }}</SectionHeader>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && stats">
        <!-- Summary cards -->
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <NeutralContainer class="text-center">
            <StatValue>{{ stats.memberCount }}</StatValue>
            <p class="text-sm text-(--text-muted)">{{ t('statistics.members') }}</p>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <StatValue>{{ Object.keys(stats.groupCounts).length }}</StatValue>
            <p class="text-sm text-(--text-muted)">{{ t('statistics.groups') }}</p>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <StatValue>{{
                stats.attendanceByMonth.reduce((s, a) => s + a.sessions, 0)
              }}</StatValue>
            <p class="text-sm text-(--text-muted)">{{ t('statistics.totalSessions') }}</p>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <StatValue>{{ stats.inventoryStatus.reduce((s, i) => s + i.total, 0) }}</StatValue>
            <p class="text-sm text-(--text-muted)">{{ t('statistics.totalItems') }}</p>
          </NeutralContainer>
        </div>

        <!-- Charts -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <NeutralContainer>
            <VChart :option="groupPieOption" autoresize style="height: 300px"/>
          </NeutralContainer>

          <NeutralContainer>
            <VChart :option="sessionsLineOption" autoresize style="height: 300px"/>
          </NeutralContainer>

          <NeutralContainer class="lg:col-span-2">
            <VChart :option="attendanceBarOption" autoresize style="height: 350px"/>
          </NeutralContainer>

          <NeutralContainer>
            <VChart :option="inventoryBarOption" autoresize style="height: 300px"/>
          </NeutralContainer>

          <NeutralContainer v-if="stats.eventRegistrations.length > 0">
            <VChart :option="registrationBarOption" autoresize style="height: 300px"/>
          </NeutralContainer>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
