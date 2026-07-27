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
  type AdminStationUsage,
  type StorageQuotaPreset,
  getAdminUsage,
  getPresets,
  recalculateAll,
} from '@/api/storageMonitoring'
import {STORAGE_CATEGORY_COLORS, buildStorageCategoryLabeler, formatBytes} from '@/util/storage'
import {useAsyncAction} from '@/composables/useAsyncAction'

use([CanvasRenderer, BarChart, PieChart, TitleComponent, TooltipComponent, GridComponent, LegendComponent])

const {t} = useI18n()

const isDark = ref(document.documentElement.classList.contains('dark'))
let observer: MutationObserver | null = null

const loading = ref(true)
const loadError = ref('')
const stations = ref<AdminStationUsage[]>([])
const presets = ref<StorageQuotaPreset[]>([])

onMounted(() => {
  observer = new MutationObserver(() => {
    isDark.value = document.documentElement.classList.contains('dark')
  })
  observer.observe(document.documentElement, {attributes: true, attributeFilter: ['class']})
  loadData()
})

onUnmounted(() => {
  observer?.disconnect()
})

async function loadData() {
  loading.value = true
  loadError.value = ''
  try {
    const [usageData, presetData] = await Promise.all([getAdminUsage(), getPresets()])
    stations.value = usageData
    presets.value = presetData
  } catch (e: any) {
    loadError.value = e.message || 'Failed to load storage data'
  } finally {
    loading.value = false
  }
}

const totalUsage = computed(() => stations.value.reduce((s, st) => s + st.totalBytes, 0))
const stationsWarning = computed(() => stations.value.filter(s => s.quotaUsedPercent >= 80 && s.quotaUsedPercent < 95).length)
const stationsFull = computed(() => stations.value.filter(s => s.quotaUsedPercent >= 95).length)

const textColor = computed(() => isDark.value ? '#e0e0e0' : '#333333')

const categoryLabel = buildStorageCategoryLabeler(t)

const {running: reconciling, error: reconcileError, run: handleRecalculateAll} = useAsyncAction(async () => {
  await recalculateAll()
  setTimeout(() => loadData(), 2000)
})

const error = computed(() => loadError.value || reconcileError.value)

const topStationsChart = computed(() => {
  const top = [...stations.value].filter(s => s.totalBytes > 0).sort((a, b) => b.totalBytes - a.totalBytes).slice(0, 15)
  return {
    tooltip: {trigger: 'axis', axisPointer: {type: 'shadow'}, formatter: (params: any) => {
      const p = Array.isArray(params) ? params[0] : params
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
    tooltip: {trigger: 'item', formatter: (p: any) => `${p.name}: ${formatBytes(p.value)} (${p.percent}%)`},
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

      <StoragePresetPanel :presets="presets" :stations="stations" @reload="loadData"/>
      <StorageStationTable :stations="stations" @reload="loadData"/>
    </template>
  </ViewContent>
</template>
