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
import {traffic} from '@/api'
import type {AuthBucketName, HourlyTrafficRow} from '@/api/traffic'
import TrafficChart from './admintrafficview/TrafficChart.vue'
import TrafficTotals from './admintrafficview/TrafficTotals.vue'
import TrafficWindowSelector from './admintrafficview/TrafficWindowSelector.vue'

const {t, n} = useI18n()

const windowHours = ref(72)
const metric = ref<'ingressBytes' | 'egressBytes' | 'requests'>('egressBytes')
const authFilter = ref<AuthBucketName | ''>('')
const rows = ref<HourlyTrafficRow[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const to = new Date()
    const from = new Date(to.getTime() - windowHours.value * 3600_000)
    const res = await traffic.getAdminHourly({
      from: from.toISOString(),
      to: to.toISOString(),
      auth: authFilter.value === '' ? undefined : authFilter.value,
    })
    rows.value = res.rows
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch([windowHours, authFilter], load)

interface StationTotal {
  stationId: number | null
  ingressBytes: number
  egressBytes: number
  requests: number
}

const stationLeaderboard = computed<StationTotal[]>(() => {
  const byStation = new Map<number | null, StationTotal>()
  for (const row of rows.value) {
    const key = row.stationId
    let existing = byStation.get(key)
    if (!existing) {
      existing = {stationId: key, ingressBytes: 0, egressBytes: 0, requests: 0}
      byStation.set(key, existing)
    }
    existing.ingressBytes += row.ingressBytes
    existing.egressBytes += row.egressBytes
    existing.requests += row.requests
  }
  return [...byStation.values()].sort((a, b) => b[metric.value] - a[metric.value])
})

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  const value = bytes / Math.pow(1024, i)
  return `${value.toFixed(value >= 100 ? 0 : 1)} ${units[i]}`
}

function stationLabel(stationId: number | null): string {
  if (stationId === null) return t('traffic.stationLeaderboard.global')
  return `#${stationId}`
}
</script>

<template>
  <ViewContent>
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <PageHeader>{{ t('traffic.admin.title') }}</PageHeader>
        <MutedText tag="p" size="sm">{{ t('traffic.admin.subtitle') }}</MutedText>
      </div>
      <HelpCenterHint :to="{name: 'help-admin-traffic'}">
        {{ t('traffic.help') }}
      </HelpCenterHint>
    </div>

    <TrafficWindowSelector
        v-model:window-hours="windowHours"
        v-model:metric="metric"
        v-model:auth-filter="authFilter"/>

    <Spinner v-if="loading"/>
    <template v-else>
      <TrafficTotals :rows="rows"/>

      <NeutralContainer class="space-y-2">
        <SectionHeader>{{ t('traffic.chart.title') }}</SectionHeader>
        <MutedText tag="p" size="sm">{{ t('traffic.chart.hint') }}</MutedText>
        <TrafficChart v-if="rows.length > 0" :rows="rows" :metric="metric"/>
        <MutedText v-else tag="div" size="sm">{{ t('traffic.noData') }}</MutedText>
      </NeutralContainer>

      <NeutralContainer class="space-y-2">
        <SectionHeader>{{ t('traffic.stationLeaderboard.title') }}</SectionHeader>
        <MutedText tag="p" size="sm">{{ t('traffic.stationLeaderboard.hint') }}</MutedText>
        <table v-if="stationLeaderboard.length > 0" class="w-full text-sm">
          <thead>
          <tr class="text-left text-(--text-muted)">
            <th class="py-1 pr-3 font-medium">{{ t('traffic.stationLeaderboard.station') }}</th>
            <th class="py-1 pr-3 font-medium">{{ t('traffic.totals.ingress') }}</th>
            <th class="py-1 pr-3 font-medium">{{ t('traffic.totals.egress') }}</th>
            <th class="py-1 pr-3 font-medium">{{ t('traffic.totals.requests') }}</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="entry in stationLeaderboard"
              :key="entry.stationId ?? 'global'"
              class="border-t border-(--border)">
            <td class="py-1 pr-3 font-mono">{{ stationLabel(entry.stationId) }}</td>
            <td class="py-1 pr-3 whitespace-nowrap">{{ formatBytes(entry.ingressBytes) }}</td>
            <td class="py-1 pr-3 whitespace-nowrap">{{ formatBytes(entry.egressBytes) }}</td>
            <td class="py-1 pr-3 whitespace-nowrap">{{ n(entry.requests) }}</td>
          </tr>
          </tbody>
        </table>
        <MutedText v-else tag="div" size="sm">{{ t('traffic.noData') }}</MutedText>
      </NeutralContainer>
    </template>
  </ViewContent>
</template>
