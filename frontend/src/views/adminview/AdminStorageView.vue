/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {use} from 'echarts/core'
import {CanvasRenderer} from 'echarts/renderers'
import {BarChart, PieChart} from 'echarts/charts'
import {GridComponent, LegendComponent, TitleComponent, TooltipComponent} from 'echarts/components'
import VChart from 'vue-echarts'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import StatValue from '@/components/typography/StatValue.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {RouterLink} from 'vue-router'
import StoragePresetPanel from './adminstorageview/StoragePresetPanel.vue'
import StorageStationTable from './adminstorageview/StorageStationTable.vue'
import {
  applyPreset,
  createPreset,
  deletePreset,
  getAdminUsage,
  getPresets,
  recalculateAll,
  recalculateStation,
  resetStationQuotas,
  updatePreset,
} from '@/api/storageMonitoring'
import {STORAGE_CATEGORY_COLORS, buildStorageCategoryLabeler, formatBytes} from '@/util/storage'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useStorageQuotas, type StorageQuotasPort} from '@/composables/useStorageQuotas'
import {darkThemeActive as isDark} from '@/util/themeState'

use([CanvasRenderer, BarChart, PieChart, TitleComponent, TooltipComponent, GridComponent, LegendComponent])

/** The subset of an ECharts tooltip callback payload these charts read. */
interface TooltipParam {
  name: string
  value: number
  dataIndex: number
  percent: number
}

const {t} = useI18n()

/**
 * The instance's own quotas: every station on it, the tiers it keeps, and the count of what is really there.
 *
 * <p>A station that answers to an association is shown and left alone. What the instance grants such a
 * station is the pool its association hands out of, so a number set here would change nothing anybody sees.
 */
const port: StorageQuotasPort = {
  load: async () => {
    const [stations, tiers] = await Promise.all([getAdminUsage(), getPresets()])
    return {stations, tiers}
  },
  createTier: (values) => createPreset(values),
  updateTier: (tierId, values) => updatePreset(tierId, values),
  deleteTier: (tierId) => deletePreset(tierId),
  applyTier: (tierId, stationIds) => applyPreset(tierId, stationIds),
  resetStation: (stationId) => resetStationQuotas(stationId),
  recalculateStation: (stationId) => recalculateStation(stationId),
}

const {
  stations, tiers: presets, loading, error: loadError, reload,
  saveTier, removeTier, applyTier, resetStation, recalculateStation: recount,
} = useStorageQuotas(port, {canRecalculate: true, showsOrigin: false, deferToCluster: true})

onMounted(reload)

const totalUsage = computed(() => stations.value.reduce((s, st) => s + st.totalBytes, 0))
const stationsWarning = computed(() => stations.value.filter(s => s.quotaUsedPercent >= 80 && s.quotaUsedPercent < 95).length)
const stationsFull = computed(() => stations.value.filter(s => s.quotaUsedPercent >= 95).length)

const textColor = computed(() => isDark.value ? '#e0e0e0' : '#333333')

const categoryLabel = buildStorageCategoryLabeler(t)

const {running: reconciling, error: reconcileError, run: handleRecalculateAll} = useAsyncAction(async () => {
  await recalculateAll()
  setTimeout(() => reload(), 2000)
})

const error = computed(() => loadError.value || reconcileError.value)

const topStationsChart = computed(() => {
  const top = [...stations.value].filter(s => s.totalBytes > 0).sort((a, b) => b.totalBytes - a.totalBytes).slice(0, 15)
  return {
    tooltip: {trigger: 'axis', axisPointer: {type: 'shadow'}, formatter: (params: TooltipParam | TooltipParam[]) => {
      const p = Array.isArray(params) ? params[0] : params
      if (!p) return ''
      return `${p.name}<br/>${formatBytes(p.value)} / ${formatBytes(top[p.dataIndex]?.quotaBytes || 0)}`
    }},
    grid: {left: '3%', right: '4%', bottom: '3%', containLabel: true},
    xAxis: {type: 'category', data: top.map(s => s.stationName), axisLabel: {color: textColor.value, rotate: 30, fontSize: 11}},
    yAxis: {type: 'value', axisLabel: {color: textColor.value, formatter: (v: number) => formatBytes(v)}},
    series: [{
      type: 'bar',
      data: top.map(s => ({
        value: s.totalBytes,
        itemStyle: {color: s.quotaUsedPercent >= 95 ? '#ec2929' : s.quotaUsedPercent >= 80 ? '#ffdd1b' : '#73CEFF'},
      })),
    }],
  }
})

const categoryPieChart = computed(() => {
  const catTotals: Record<string, number> = {}
  for (const station of stations.value) {
    for (const cat of station.categories) {
      if (cat.category === 'IMAGE_AVATAR') continue
      catTotals[cat.category] = (catTotals[cat.category] || 0) + cat.totalBytes
    }
  }
  return {
    tooltip: {trigger: 'item', formatter: (p: TooltipParam) => `${p.name}: ${formatBytes(p.value)} (${p.percent}%)`},
    legend: {bottom: 0, textStyle: {color: textColor.value}},
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      data: Object.entries(catTotals).filter(([, bytes]) => bytes > 0).map(([cat, bytes]) => ({name: categoryLabel(cat), value: bytes, itemStyle: {color: STORAGE_CATEGORY_COLORS[cat] || '#9ca3af'}})),
      label: {color: textColor.value},
    }],
  }
})
</script>

<template>
  <ViewContent :title="t('pages.admin-storage.title')" :subtitle="t('pages.admin-storage.subtitle')">
    <div class="mb-4 flex justify-end gap-4 text-sm">
      <RouterLink :to="{name: 'admin-storage-backend'}" class="underline">
        {{ t('adminStorageBackend.linkFromUsage') }}
      </RouterLink>
      <RouterLink :to="{name: 'admin-storage-audit'}" class="underline">
        {{ t('adminStorageAudit.title') }}
      </RouterLink>
    </div>
    <Spinner v-if="loading" size="lg"/>
    <Alert v-else-if="error" variant="error">{{ error }}</Alert>
    <template v-else>
      <!-- Summary -->
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <NeutralContainer class="text-center">
          <StatValue>{{ formatBytes(totalUsage) }}</StatValue>
          <p class="text-sm text-(--text-muted)">{{ t('storageMonitoring.totalUsage') }}</p>
        </NeutralContainer>
        <NeutralContainer class="text-center">
          <StatValue>{{ stations.length }}</StatValue>
          <p class="text-sm text-(--text-muted)">{{ t('storageMonitoring.totalStations') }}</p>
        </NeutralContainer>
        <NeutralContainer class="text-center">
          <StatValue color="error">{{ stationsWarning }}</StatValue>
          <p class="text-sm text-(--text-muted)">{{ t('storageMonitoring.stationsWarning') }}</p>
        </NeutralContainer>
        <NeutralContainer class="text-center">
          <StatValue color="error">{{ stationsFull }}</StatValue>
          <p class="text-sm text-(--text-muted)">{{ t('storageMonitoring.stationsFull') }}</p>
        </NeutralContainer>
      </div>

      <!-- Charts -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-6">
        <NeutralContainer>
          <SectionHeader>{{ t('storageMonitoring.stationOverview') }}</SectionHeader>
          <VChart v-if="stations.length > 0" :option="topStationsChart" autoresize style="height: 300px"/>
        </NeutralContainer>
        <NeutralContainer>
          <SectionHeader>{{ t('storageMonitoring.categoryBreakdown') }}</SectionHeader>
          <VChart v-if="stations.length > 0" :option="categoryPieChart" autoresize style="height: 300px"/>
        </NeutralContainer>
      </div>

      <!-- Actions -->
      <div class="flex flex-wrap gap-2 mb-6">
        <PrimaryButton :disabled="reconciling" @click="handleRecalculateAll">
          <font-awesome-icon :icon="['fas', 'arrows-rotate']" class="mr-1"/>
          {{ t('storageMonitoring.recalculateAll') }}
        </PrimaryButton>
      </div>

      <StoragePresetPanel :stations="stations" :tiers="presets"
                          @apply="applyTier" @remove="removeTier" @save="saveTier"/>
      <StorageStationTable :stations="stations" @recalculate="recount" @reset="resetStation"/>
    </template>
  </ViewContent>
</template>
