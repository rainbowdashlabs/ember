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
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import client from '@/api/client'
import {cartesianGrid, chartTitle, DONUT_CENTER, DONUT_RADIUS} from '@/util/chartLayout'
import EmailStatsSection from './adminstatisticsview/EmailStatsSection.vue'
import PlatformStatsSection from './adminstatisticsview/PlatformStatsSection.vue'
import GrowthStatsSection from './adminstatisticsview/GrowthStatsSection.vue'
import HealthStatsSection from './adminstatisticsview/HealthStatsSection.vue'
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
  mailProviderBlocks: number
  accountsWith2fa: number
  eventsUpcoming: number
  totalEventRegistrations: number
  attendanceByStatus: Array<{ status: string; cnt: number }>
  registrationsByDay: Array<{ day: string; count: number }>
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
    title: chartTitle(t('adminStats.emailHistory'), textColor.value),
    tooltip: {trigger: 'axis'},
    grid: cartesianGrid({title: true, rotatedLabels: true, left: 50}),
    xAxis: {type: 'category', data: days.map(d => d.day), axisLabel: {rotate: 45, fontSize: 10, color: mutedColor.value}, axisLine: {lineStyle: {color: mutedColor.value}}},
    yAxis: {type: 'value', minInterval: 1, axisLabel: {color: mutedColor.value}, axisLine: {lineStyle: {color: mutedColor.value}}, splitLine: {lineStyle: {color: isDark.value ? '#333' : '#e0e0e0'}}},
    series: [{type: 'bar', data: days.map(d => d.count), color: '#FF6421'}],
  }
})

function dailyLineOption(title: string, series: Array<{day: string; count: number}>, color: string) {
  return {
    title: chartTitle(title, textColor.value),
    tooltip: {trigger: 'axis'},
    grid: cartesianGrid({title: true, rotatedLabels: true, left: 50}),
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

const registrationsByDayOption = computed(() => {
  if (!stats.value) return {}
  return dailyLineOption(t('adminStats.registrationsGrowth'), stats.value.registrationsByDay, '#3694FF')
})

const topStationsOption = computed(() => {
  if (!stats.value || stats.value.topStationsByMembers.length === 0) return {}
  const rows = [...stats.value.topStationsByMembers].reverse()
  return {
    title: chartTitle(t('adminStats.topStations'), textColor.value),
    tooltip: {trigger: 'axis', axisPointer: {type: 'shadow'}},
    grid: cartesianGrid({title: true, left: 120}),
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

function donut(title: string, data: unknown[]) {
  return {
    title: chartTitle(title, textColor.value),
    tooltip: {trigger: 'item', formatter: '{b}: {c} ({d}%)'},
    series: [{
      type: 'pie',
      radius: DONUT_RADIUS,
      center: DONUT_CENTER,
      label: {color: mutedColor.value},
      data,
    }],
  }
}

const verifiedOption = computed(() => {
  if (!stats.value) return {}
  return donut(t('adminStats.verificationStatus'), [
    {name: t('adminStats.verified'), value: stats.value.accountsVerified, itemStyle: {color: '#00C507'}},
    {name: t('adminStats.unverified'), value: stats.value.accountsUnverified, itemStyle: {color: '#ec2929'}},
  ])
})

const setupOption = computed(() => {
  if (!stats.value) return {}
  return donut(t('adminStats.setupStatus'), [
    {name: t('adminStats.setupComplete'), value: stats.value.stationsSetupComplete, itemStyle: {color: '#00C507'}},
    {name: t('adminStats.setupPending'), value: stats.value.stationsSetupPending, itemStyle: {color: '#ffdd1b'}},
  ])
})

const attendanceColors: Record<string, string> = {
  PRESENT: '#00C507', ABSENT: '#ec2929', EXCUSED: '#ffdd1b', DECLINED: '#CFCFCF',
}

function attendanceLabel(status: string): string {
  const key = `adminStats.attendanceStatusLabels.${status}`
  return te(key) ? t(key) : status
}

const attendanceStatusOption = computed(() => {
  if (!stats.value || !stats.value.attendanceByStatus.length) return {}
  return donut(
      t('adminStats.attendanceStatus'),
      stats.value.attendanceByStatus.map(e => ({
        name: attendanceLabel(e.status),
        value: e.cnt,
        itemStyle: {color: attendanceColors[e.status] ?? '#CFCFCF'},
      })),
  )
})

const emailStatusOption = computed(() => {
  if (!stats.value || !stats.value.emailByStatus.length) return {}
  return donut(
      t('adminStats.emailStatus'),
      stats.value.emailByStatus.map(e => ({
        name: statusLabel(e.status),
        value: e.cnt,
        itemStyle: {color: statusColors[e.status] ?? '#CFCFCF'},
      })),
  )
})
</script>

<template>
  <ViewContent :title="t('pages.admin-statistics.title')" :subtitle="t('pages.admin-statistics.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && stats">
        <EmailStatsSection
            :email-sent-today="stats.emailSentToday"
            :email-pending="stats.emailPending"
            :email-sending="stats.emailSending"
            :email-sent="stats.emailSent"
            :email-failed="stats.emailFailed"
            :mail-provider-blocks="stats.mailProviderBlocks"
            :email-by-day-count="stats.emailByDay.length"
            :email-by-status-count="stats.emailByStatus.length"
            :email-by-day-option="emailByDayOption"
            :email-status-option="emailStatusOption"/>
        <PlatformStatsSection
            :total-stations="stats.totalStations"
            :total-accounts="stats.totalAccounts"
            :total-members="stats.totalMembers"
            :active-sessions="stats.activeSessions"
            :total-groups="stats.totalGroups"
            :two-factor-accounts="stats.accountsWith2fa"/>

        <GrowthStatsSection
            :sessions-by-day-count="stats.sessionsByDay.length"
            :registrations-by-day-count="stats.registrationsByDay.length"
            :top-stations-count="stats.topStationsByMembers.length"
            :sessions-by-day-option="sessionsByDayOption"
            :registrations-by-day-option="registrationsByDayOption"
            :top-stations-option="topStationsOption"/>
        <HealthStatsSection
            :verified-count="stats.accountsVerified + stats.accountsUnverified"
            :setup-count="stats.stationsSetupComplete + stats.stationsSetupPending"
            :attendance-status-count="stats.attendanceByStatus.length"
            :verified-option="verifiedOption"
            :setup-option="setupOption"
            :attendance-status-option="attendanceStatusOption"/>
        <DataStatsSection
            :total-events="stats.totalEvents"
            :events-upcoming="stats.eventsUpcoming"
            :total-event-registrations="stats.totalEventRegistrations"
            :total-attendance-sessions="stats.totalAttendanceSessions"
            :sessions-this-month="stats.sessionsThisMonth"
            :total-attendance-entries="stats.totalAttendanceEntries"
            :total-inventory-items="stats.totalInventoryItems"
            :total-profile-fields="stats.totalProfileFields"/>
      </template>
    </div>
  </ViewContent>
</template>
