/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {use} from 'echarts/core'
import {CanvasRenderer} from 'echarts/renderers'
import {BarChart, PieChart} from 'echarts/charts'
import {GridComponent, TitleComponent, TooltipComponent} from 'echarts/components'
import VChart from 'vue-echarts'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import client from '@/api/client'

use([CanvasRenderer, BarChart, PieChart, TitleComponent, TooltipComponent, GridComponent])

const {t} = useI18n()

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
}

const stats = ref<AdminStats | null>(null)
const loading = ref(true)
const error = ref('')

const statusLabels: Record<string, string> = {
  PENDING: 'Warteschlange', SENDING: 'Wird gesendet', SENT: 'Gesendet', FAILED: 'Fehlgeschlagen',
}
const statusColors: Record<string, string> = {
  PENDING: '#ffdd1b', SENDING: '#3694FF', SENT: '#00C507', FAILED: '#ec2929',
}

async function loadStats() {
  loading.value = true
  error.value = ''
  try {
    stats.value = (await client.get<AdminStats>('/admin/statistics')).data
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

const emailByDayOption = computed(() => {
  if (!stats.value || !stats.value.emailByDay.length) return {}
  const days = [...stats.value.emailByDay].reverse()
  return {
    title: {text: t('adminStats.emailHistory'), left: 'center', textStyle: {fontSize: 14}},
    tooltip: {trigger: 'axis'},
    grid: {left: 50, right: 20, top: 40, bottom: 20},
    xAxis: {type: 'category', data: days.map(d => d.day), axisLabel: {rotate: 45, fontSize: 10}},
    yAxis: {type: 'value', minInterval: 1},
    series: [{type: 'bar', data: days.map(d => d.count), color: '#FF6421'}],
  }
})

const emailStatusOption = computed(() => {
  if (!stats.value || !stats.value.emailByStatus.length) return {}
  return {
    title: {text: t('adminStats.emailStatus'), left: 'center', textStyle: {fontSize: 14}},
    tooltip: {trigger: 'item', formatter: '{b}: {c} ({d}%)'},
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      data: stats.value.emailByStatus.map(e => ({
        name: statusLabels[e.status] ?? e.status, value: e.cnt,
        itemStyle: {color: statusColors[e.status] ?? '#CFCFCF'},
      })),
    }],
  }
})

onMounted(loadStats)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SectionHeader>{{ t('adminStats.title') }}</SectionHeader>
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && stats">
        <!-- Email -->
        <SubHeader>{{ t('adminStats.emailSection') }}</SubHeader>
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <NeutralContainer class="text-center">
            <p class="text-2xl font-bold text-success">{{ stats.emailSentToday }}</p>
            <p class="text-sm text-(--text-muted)">{{ t('adminStats.sentToday') }}</p>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <p :class="stats.emailPending > 0 ? 'text-info' : ''" class="text-2xl font-bold">{{
                stats.emailPending
              }}</p>
            <p class="text-sm text-(--text-muted)">{{ t('adminStats.queued') }}</p>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <p class="text-2xl font-bold">{{ stats.emailSent }}</p>
            <p class="text-sm text-(--text-muted)">{{ t('adminStats.totalSent') }}</p>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <p :class="stats.emailFailed > 0 ? 'text-error' : ''" class="text-2xl font-bold">{{ stats.emailFailed }}</p>
            <p class="text-sm text-(--text-muted)">{{ t('adminStats.failed') }}</p>
          </NeutralContainer>
        </div>
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <NeutralContainer v-if="stats.emailByDay.length > 0">
            <VChart :option="emailByDayOption" autoresize style="height: 300px"/>
          </NeutralContainer>
          <NeutralContainer v-if="stats.emailByStatus.length > 0">
            <VChart :option="emailStatusOption" autoresize style="height: 300px"/>
          </NeutralContainer>
        </div>

        <!-- Platform totals -->
        <SubHeader>{{ t('adminStats.platformSection') }}</SubHeader>
        <div class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-5 gap-4">
          <NeutralContainer class="text-center">
            <p class="text-2xl font-bold text-primary">{{ stats.totalStations }}</p>
            <p class="text-sm text-(--text-muted)">{{ t('adminStats.stations') }}</p>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <p class="text-2xl font-bold text-primary">{{ stats.totalAccounts }}</p>
            <p class="text-sm text-(--text-muted)">{{ t('adminStats.accounts') }}</p>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <p class="text-2xl font-bold text-primary">{{ stats.totalMembers }}</p>
            <p class="text-sm text-(--text-muted)">{{ t('adminStats.members') }}</p>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <p class="text-2xl font-bold text-primary">{{ stats.activeSessions }}</p>
            <p class="text-sm text-(--text-muted)">{{ t('adminStats.activeSessions') }}</p>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <p class="text-2xl font-bold text-primary">{{ stats.totalGroups }}</p>
            <p class="text-sm text-(--text-muted)">{{ t('adminStats.groups') }}</p>
          </NeutralContainer>
        </div>

        <!-- Data totals -->
        <SubHeader>{{ t('adminStats.dataSection') }}</SubHeader>
        <div class="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-5 gap-4">
          <NeutralContainer class="text-center">
            <p class="text-2xl font-bold text-primary">{{ stats.totalEvents }}</p>
            <p class="text-sm text-(--text-muted)">{{ t('adminStats.events') }}</p>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <p class="text-2xl font-bold text-primary">{{ stats.totalAttendanceSessions }}</p>
            <p class="text-sm text-(--text-muted)">{{ t('adminStats.attendanceSessions') }}</p>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <p class="text-2xl font-bold text-primary">{{ stats.totalAttendanceEntries }}</p>
            <p class="text-sm text-(--text-muted)">{{ t('adminStats.attendanceEntries') }}</p>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <p class="text-2xl font-bold text-primary">{{ stats.totalInventoryItems }}</p>
            <p class="text-sm text-(--text-muted)">{{ t('adminStats.inventoryItems') }}</p>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <p class="text-2xl font-bold text-primary">{{ stats.totalProfileFields }}</p>
            <p class="text-sm text-(--text-muted)">{{ t('adminStats.profileFields') }}</p>
          </NeutralContainer>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
