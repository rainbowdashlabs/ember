/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {use} from 'echarts/core'
import {CanvasRenderer} from 'echarts/renderers'
import {BarChart, LineChart, PieChart} from 'echarts/charts'
import {GridComponent, LegendComponent, TitleComponent, TooltipComponent} from 'echarts/components'
import VChart from 'vue-echarts'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import client from '@/api/client'
import EmailStatsSection from './adminstatisticsview/EmailStatsSection.vue'
import PlatformStatsSection from './adminstatisticsview/PlatformStatsSection.vue'
import DataStatsSection from './adminstatisticsview/DataStatsSection.vue'
import {useConfigPanel} from '@/composables/useConfigPanel'

use([CanvasRenderer, BarChart, LineChart, PieChart, TitleComponent, TooltipComponent, GridComponent, LegendComponent])

const {t, te} = useI18n()

const isDark = ref(document.documentElement.classList.contains('dark'))
let observer: MutationObserver | null = null

onMounted(() => {
  observer = new MutationObserver(() => {
    isDark.value = document.documentElement.classList.contains('dark')
  })
  observer.observe(document.documentElement, {attributes: true, attributeFilter: ['class']})
})

onUnmounted(() => {
  observer?.disconnect()
})

const textColor = computed(() => isDark.value ? '#e0e0e0' : '#333333')
const mutedColor = computed(() => isDark.value ? '#9ca3af' : '#666666')

interface AdminStats {
  emailPending: number
  emailSending: number
  emailSentToday: number
  emailFailed: number
  emailSent: number
  emailByDay: Array<{ day: string; count: number }>
  emailByStatus: Array<{ status: string; cnt: number }>
  totalAccounts: number
  totalStations: number
  totalMembers: number
  activeSessions: number
  pendingApplications: number
  sessionsThisMonth: number
  totalInventoryItems: number
  totalEvents: number
  totalAttendanceSessions: number
  totalAttendanceEntries: number
  totalProfileFields: number
  totalGroups: number
  accountsVerified: number
  accountsUnverified: number
  stationsSetupComplete: number
  stationsSetupPending: number
  sessionsByDay: Array<{ day: string; count: number }>
  topStationsByMembers: Array<{ name: string; member_count: number }>
}

const statusColors: Record<string, string> = {
  PENDING: '#ffdd1b', SENDING: '#3694FF', SENT: '#00C507', FAILED: '#ec2929',
}

function statusLabel(status: string): string {
  const key = `adminStats.emailStatusLabels.${status}`
  return te(key) ? t(key) : status
}

const {config: stats, loading, error} = useConfigPanel<AdminStats | null>({
  initial: null,
  fetch: async () => (await client.get<AdminStats>('/admin/statistics')).data,
})

const emailByDayOption = computed(() => {
  if (!stats.value || !stats.value.emailByDay.length) return {}
  const days = [...stats.value.emailByDay].reverse()
  return {
    title: {text: t('adminStats.emailHistory'), left: 'center', textStyle: {fontSize: 14, color: textColor.value}},
    tooltip: {trigger: 'axis'},
    grid: {left: 50, right: 20, top: 40, bottom: 20},
    xAxis: {type: 'category', data: days.map(d => d.day), axisLabel: {rotate: 45, fontSize: 10, color: mutedColor.value}, axisLine: {lineStyle: {color: mutedColor.value}}},
    yAxis: {type: 'value', minInterval: 1, axisLabel: {color: mutedColor.value}, axisLine: {lineStyle: {color: mutedColor.value}}, splitLine: {lineStyle: {color: isDark.value ? '#333' : '#e0e0e0'}}},
    series: [{type: 'bar', data: days.map(d => d.count), color: '#FF6421'}],
  }
})

function dailyLineOption(title: string, series: Array<{day: string; count: number}>, color: string) {
  return {
    title: {text: title, left: 'center', textStyle: {fontSize: 14, color: textColor.value}},
    tooltip: {trigger: 'axis'},
    grid: {left: 50, right: 20, top: 40, bottom: 30},
    xAxis: {
      type: 'category',
      data: series.map(d => d.day),
      axisLabel: {rotate: 45, fontSize: 10, color: mutedColor.value},
      axisLine: {lineStyle: {color: mutedColor.value}},
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: {color: mutedColor.value},
      axisLine: {lineStyle: {color: mutedColor.value}},
      splitLine: {lineStyle: {color: isDark.value ? '#333' : '#e0e0e0'}},
    },
    series: [{
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      data: series.map(d => d.count),
      areaStyle: {opacity: 0.15},
      lineStyle: {color, width: 2},
      itemStyle: {color},
    }],
  }
}

const sessionsByDayOption = computed(() => {
  if (!stats.value) return {}
  return dailyLineOption(t('adminStats.sessionsGrowth'), stats.value.sessionsByDay, '#00C507')
})

const topStationsOption = computed(() => {
  if (!stats.value || stats.value.topStationsByMembers.length === 0) return {}
  const rows = [...stats.value.topStationsByMembers].reverse()
  return {
    title: {text: t('adminStats.topStations'), left: 'center', textStyle: {fontSize: 14, color: textColor.value}},
    tooltip: {trigger: 'axis', axisPointer: {type: 'shadow'}},
    grid: {left: 120, right: 30, top: 40, bottom: 30},
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: {color: mutedColor.value},
      axisLine: {lineStyle: {color: mutedColor.value}},
      splitLine: {lineStyle: {color: isDark.value ? '#333' : '#e0e0e0'}},
    },
    yAxis: {
      type: 'category',
      data: rows.map(r => r.name),
      axisLabel: {color: mutedColor.value, fontSize: 11},
      axisLine: {lineStyle: {color: mutedColor.value}},
    },
    series: [{
      type: 'bar',
      data: rows.map(r => r.member_count),
      itemStyle: {color: '#FF6421'},
    }],
  }
})

const verifiedOption = computed(() => {
  if (!stats.value) return {}
  return {
    title: {text: t('adminStats.verificationStatus'), left: 'center', textStyle: {fontSize: 14, color: textColor.value}},
    tooltip: {trigger: 'item', formatter: '{b}: {c} ({d}%)'},
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      label: {color: mutedColor.value},
      data: [
        {name: t('adminStats.verified'), value: stats.value.accountsVerified, itemStyle: {color: '#00C507'}},
        {name: t('adminStats.unverified'), value: stats.value.accountsUnverified, itemStyle: {color: '#ec2929'}},
      ],
    }],
  }
})

const setupOption = computed(() => {
  if (!stats.value) return {}
  return {
    title: {text: t('adminStats.setupStatus'), left: 'center', textStyle: {fontSize: 14, color: textColor.value}},
    tooltip: {trigger: 'item', formatter: '{b}: {c} ({d}%)'},
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      label: {color: mutedColor.value},
      data: [
        {name: t('adminStats.setupComplete'), value: stats.value.stationsSetupComplete, itemStyle: {color: '#00C507'}},
        {name: t('adminStats.setupPending'), value: stats.value.stationsSetupPending, itemStyle: {color: '#ffdd1b'}},
      ],
    }],
  }
})

const emailStatusOption = computed(() => {
  if (!stats.value || !stats.value.emailByStatus.length) return {}
  return {
    title: {text: t('adminStats.emailStatus'), left: 'center', textStyle: {fontSize: 14, color: textColor.value}},
    tooltip: {trigger: 'item', formatter: '{b}: {c} ({d}%)'},
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      label: {color: mutedColor.value},
      data: stats.value.emailByStatus.map(e => ({
        name: statusLabel(e.status), value: e.cnt,
        itemStyle: {color: statusColors[e.status] ?? '#CFCFCF'},
      })),
    }],
  }
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SectionHeader>{{ t('adminStats.title') }}</SectionHeader>
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && stats">
        <EmailStatsSection
            :email-sent-today="stats.emailSentToday"
            :email-pending="stats.emailPending"
            :email-sent="stats.emailSent"
            :email-failed="stats.emailFailed"
            :email-by-day-count="stats.emailByDay.length"
            :email-by-status-count="stats.emailByStatus.length"
            :email-by-day-option="emailByDayOption"
            :email-status-option="emailStatusOption"/>
        <PlatformStatsSection
            :total-stations="stats.totalStations"
            :total-accounts="stats.totalAccounts"
            :total-members="stats.totalMembers"
            :active-sessions="stats.activeSessions"
            :total-groups="stats.totalGroups"/>

        <SubHeader>{{ t('adminStats.growthSection') }}</SubHeader>
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <NeutralContainer>
            <VChart v-if="stats.sessionsByDay.length > 0" :option="sessionsByDayOption" autoresize style="height: 260px"/>
          </NeutralContainer>
          <NeutralContainer>
            <VChart v-if="stats.topStationsByMembers.length > 0" :option="topStationsOption" autoresize style="height: 260px"/>
          </NeutralContainer>
        </div>

        <SubHeader>{{ t('adminStats.healthSection') }}</SubHeader>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <NeutralContainer>
            <VChart v-if="stats.accountsVerified + stats.accountsUnverified > 0" :option="verifiedOption" autoresize style="height: 260px"/>
          </NeutralContainer>
          <NeutralContainer>
            <VChart v-if="stats.stationsSetupComplete + stats.stationsSetupPending > 0" :option="setupOption" autoresize style="height: 260px"/>
          </NeutralContainer>
        </div>

        <DataStatsSection
            :total-events="stats.totalEvents"
            :total-attendance-sessions="stats.totalAttendanceSessions"
            :total-attendance-entries="stats.totalAttendanceEntries"
            :total-inventory-items="stats.totalInventoryItems"
            :total-profile-fields="stats.totalProfileFields"/>
      </template>
    </div>
  </ViewContent>
</template>
