/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import HelpCenterHint from '@/components/help/HelpCenterHint.vue'
import {stations, traffic} from '@/api'
import type {AuthBucketName, HourlyTrafficRow} from '@/api/traffic'
import type {Station} from '@/api/types'
import TrafficTotals from './admintrafficview/TrafficTotals.vue'
import TrafficWindowSelector from './admintrafficview/TrafficWindowSelector.vue'
import TrafficChartCard from './admintrafficview/TrafficChartCard.vue'
import TrafficLeaderboard, {type StationTotal} from './admintrafficview/TrafficLeaderboard.vue'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()

const windowHours = ref(72)
const metric = ref<'ingressBytes' | 'egressBytes' | 'requests' | 'inout'>('egressBytes')
const authFilter = ref<AuthBucketName | ''>('')
const rows = ref<HourlyTrafficRow[]>([])
const stationNames = ref<Map<string, string>>(new Map())

const {loading, reload} = useAsyncLoader(async () => {
  const to = new Date()
  const from = new Date(to.getTime() - windowHours.value * 3600_000)
  const res = await traffic.getAdminHourly({
    from: from.toISOString(),
    to: to.toISOString(),
    auth: authFilter.value === '' ? undefined : authFilter.value,
  })
  rows.value = res.rows
}, {autoLoad: false})

async function loadStationNames() {
  try {
    const list: Station[] = await stations.listStations()
    const map = new Map<string, string>()
    for (const s of list) {
      const uid = String((s as unknown as {id: unknown}).id)
      map.set(uid, s.name ?? uid)
    }
    stationNames.value = map
  } catch {
    stationNames.value = new Map()
  }
}

onMounted(async () => {
  await loadStationNames()
  await reload()
})
watch([windowHours, authFilter], reload)

const stationLeaderboard = computed<StationTotal[]>(() => {
  const byStation = new Map<string | null, StationTotal>()
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
  const sortKey: 'ingressBytes' | 'egressBytes' | 'requests' = metric.value === 'inout'
      ? 'egressBytes'
      : metric.value
  return [...byStation.values()].sort((a, b) => b[sortKey] - a[sortKey])
})

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  const value = bytes / Math.pow(1024, i)
  return `${value.toFixed(value >= 100 ? 0 : 1)} ${units[i]}`
}

function stationLabel(stationId: string | null): string {
  if (stationId === null) return t('traffic.stationLeaderboard.global')
  return stationNames.value.get(stationId) ?? stationId
}
</script>

<template>
  <ViewContent>
    <div class="space-y-4">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
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
      <TrafficChartCard :rows="rows" :metric="metric"/>
      <TrafficLeaderboard
          :entries="stationLeaderboard"
          :station-label="stationLabel"
          :format-bytes="formatBytes"/>
    </template>
    </div>
  </ViewContent>
</template>
