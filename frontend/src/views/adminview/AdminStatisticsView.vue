/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {use} from 'echarts/core'
import {CanvasRenderer} from 'echarts/renderers'
import {BarChart, LineChart, PieChart} from 'echarts/charts'
import {AriaComponent, GridComponent, LegendComponent, TitleComponent, TooltipComponent} from 'echarts/components'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {cartesianGrid, chartTitle} from '@/util/chartLayout'
import EmailStatsSection from './adminstatisticsview/EmailStatsSection.vue'
import PlatformStatsSection from './adminstatisticsview/PlatformStatsSection.vue'
import ActivityStatsSection from './adminstatisticsview/ActivityStatsSection.vue'
import HealthStatsSection from './adminstatisticsview/HealthStatsSection.vue'
import DataStatsSection from './adminstatisticsview/DataStatsSection.vue'
import WindowedStatsSections from './adminstatisticsview/WindowedStatsSections.vue'
import {CATEGORY_COLORS, donutChart, seriesChart, useChartPaint} from './adminstatisticsview/instanceCharts'
import {useInstanceStatistics} from './adminstatisticsview/useInstanceStatistics'

use([
  CanvasRenderer, BarChart, LineChart, PieChart, AriaComponent, TitleComponent, TooltipComponent, GridComponent,
  LegendComponent,
])

const {t, te} = useI18n()
const paint = useChartPaint()

const statusColors: Record<string, string> = {
  PENDING: '#ffdd1b', SENDING: '#3694FF', SENT: '#00C507', FAILED: '#ec2929',
}

function statusLabel(status: string): string {
  const key = `adminStats.emailStatusLabels.${status}`
  return te(key) ? t(key) : status
}

const {statisticsWindow, stats, loading, failure} = useInstanceStatistics()

const emailByDayOption = computed(() => {
  if (!stats.value || !stats.value.emailByDay.length) return {}
  const days = [...stats.value.emailByDay].reverse()
  return seriesChart('bar', t('adminStats.emailHistory'), days.map(d => d.day),
      [{name: t('adminStats.emailHistory'), data: days.map(d => d.count), color: CATEGORY_COLORS[0]}], paint.value)
})

const signUpsByDayOption = computed(() => {
  if (!stats.value) return {}
  const days = stats.value.signUpsByDay
  return seriesChart('line', t('adminStats.registrationsGrowth'), days.map(d => d.day),
      [{name: t('adminStats.registrationsGrowth'), data: days.map(d => d.count), color: CATEGORY_COLORS[4]}], paint.value)
})

const topStationsOption = computed(() => {
  if (!stats.value || stats.value.topStationsByMembers.length === 0) return {}
  const rows = [...stats.value.topStationsByMembers].reverse()
  return {
    aria: {enabled: true},
    title: chartTitle(t('adminStats.topStations'), paint.value.text),
    tooltip: {trigger: 'axis', axisPointer: {type: 'shadow'}},
    grid: cartesianGrid({title: true, left: 120}),
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: {color: paint.value.muted},
      axisLine: {lineStyle: {color: paint.value.muted}},
      splitLine: {lineStyle: {color: paint.value.split}},
    },
    yAxis: {
      type: 'category',
      data: rows.map(r => r.name),
      axisLabel: {color: paint.value.muted, fontSize: 11},
      axisLine: {lineStyle: {color: paint.value.muted}},
    },
    series: [{type: 'bar', data: rows.map(r => r.member_count), itemStyle: {color: CATEGORY_COLORS[0]}}],
  }
})

const verifiedOption = computed(() => {
  if (!stats.value) return {}
  return donutChart(t('adminStats.verificationStatus'), [
    {name: t('adminStats.verified'), value: stats.value.accountsVerified, color: '#00C507'},
    {name: t('adminStats.unverified'), value: stats.value.accountsUnverified, color: '#ec2929'},
  ], paint.value)
})

const setupOption = computed(() => {
  if (!stats.value) return {}
  return donutChart(t('adminStats.setupStatus'), [
    {name: t('adminStats.setupComplete'), value: stats.value.stationsSetupComplete, color: '#00C507'},
    {name: t('adminStats.setupPending'), value: stats.value.stationsSetupPending, color: '#ffdd1b'},
  ], paint.value)
})

const emailStatusOption = computed(() => {
  if (!stats.value || !stats.value.emailByStatus.length) return {}
  return donutChart(
      t('adminStats.emailStatus'),
      stats.value.emailByStatus.map(e => ({
        name: statusLabel(e.status),
        value: e.cnt,
        color: statusColors[e.status] ?? '#CFCFCF',
      })),
      paint.value,
  )
})
</script>

<template>
  <ViewContent :title="t('pages.admin-statistics.title')" :subtitle="t('pages.admin-statistics.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading && !stats" size="lg"/>
      <FailureAlert :failure="failure"/>

      <template v-if="stats">
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

        <WindowedStatsSections v-model="statisticsWindow" :stats="stats" :loading="loading"/>

        <ActivityStatsSection
            :sign-ups-by-day-count="stats.signUpsByDay.length"
            :top-stations-count="stats.topStationsByMembers.length"
            :sign-ups-by-day-option="signUpsByDayOption"
            :top-stations-option="topStationsOption"/>
        <HealthStatsSection
            :verified-count="stats.accountsVerified + stats.accountsUnverified"
            :setup-count="stats.stationsSetupComplete + stats.stationsSetupPending"
            :verified-option="verifiedOption"
            :setup-option="setupOption"/>
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
