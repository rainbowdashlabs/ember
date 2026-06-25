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
import {BarChart} from 'echarts/charts'
import {GridComponent, TitleComponent, TooltipComponent} from 'echarts/components'
import VChart from 'vue-echarts'
import {RouterLink} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {type StationUsageResponse, formatBytes, getStationUsage} from '@/api/storageMonitoring'

use([CanvasRenderer, BarChart, TitleComponent, TooltipComponent, GridComponent])

const {t} = useI18n()

const isDark = ref(document.documentElement.classList.contains('dark'))
let observer: MutationObserver | null = null

const loading = ref(true)
const error = ref('')
const usage = ref<StationUsageResponse | null>(null)

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
  error.value = ''
  try {
    usage.value = await getStationUsage()
  } catch (e: any) {
    error.value = e.message || 'Failed to load storage data'
  } finally {
    loading.value = false
  }
}

const textColor = computed(() => isDark.value ? '#e0e0e0' : '#333333')

function barColor(percent: number) {
  if (percent >= 95) return 'bg-red-500'
  if (percent >= 80) return 'bg-yellow-500'
  return 'bg-green-500'
}

function categoryLabel(cat: string): string {
  const labels: Record<string, string> = {
    KB_FILES: t('storageMonitoring.categories.kbFiles'),
    BOARD_ATTACHMENTS: t('storageMonitoring.categories.boardAttachments'),
    PAGE_FILES: t('storageMonitoring.categories.pageFiles'),
    PAGE_IMAGES: t('storageMonitoring.categories.pageFiles'),
    IMAGE_AVATAR: t('storageMonitoring.categories.avatars'),
    IMAGE_LOST_AND_FOUND: t('storageMonitoring.categories.lostAndFound'),
    IMAGE_LOGO_FRAGMENT: t('storageMonitoring.categories.logoFragment'),
    IMAGE_QUIZ_QUESTION: t('storageMonitoring.categories.quizQuestion'),
    IMAGE_KB_ICON: t('storageMonitoring.categories.kbIcon'),
    IMAGE_KB_IMAGE: t('storageMonitoring.categories.kbImage'),
    DOCUMENT: t('storageMonitoring.categories.document'),
    DISCOVERY_KEY: t('storageMonitoring.categories.discoveryKey'),
    MAP_TILE_CACHE: t('storageMonitoring.categories.mapTileCache'),
    DEMO_AVATAR: t('storageMonitoring.categories.demoAvatar'),
  }
  return labels[cat] || cat
}

const chartOption = computed(() => {
  if (!usage.value) return {}
  const trackedOnly: string[] = ['IMAGE_AVATAR', 'DOCUMENT', 'DISCOVERY_KEY', 'MAP_TILE_CACHE', 'DEMO_AVATAR']
  const categories = usage.value.categories.filter(c => !trackedOnly.includes(c.category) && c.totalBytes > 0)
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: {type: 'shadow'},
      valueFormatter: (v: number | string) => formatBytes(typeof v === 'number' ? v : Number(v)),
    },
    grid: {left: '3%', right: '4%', bottom: '3%', containLabel: true},
    xAxis: {type: 'category', data: categories.map(c => categoryLabel(c.category)), axisLabel: {color: textColor.value}},
    yAxis: {type: 'value', axisLabel: {color: textColor.value, formatter: (v: number) => formatBytes(v)}},
    series: [{
      type: 'bar',
      data: categories.map(c => ({
        value: c.totalBytes,
        itemStyle: {
          color: c.totalBytes > (usage.value!.categoryQuotas[c.category] || 0) * 0.8 ? '#ec2929' : '#73CEFF',
        },
      })),
    }],
  }
})
</script>

<template>
  <ViewContent>
    <div class="mb-4 flex justify-end">
      <RouterLink :to="{name: 'station-storage-backend'}" class="text-sm underline">
        {{ t('stationStorageBackend.linkFromUsage') }}
      </RouterLink>
    </div>
    <Spinner v-if="loading" size="lg"/>
    <Alert v-else-if="error" variant="error">{{ error }}</Alert>
    <template v-else-if="usage">
      <Alert v-if="usage.usesOwnBackend" variant="info" class="mb-6">
        {{ t('storageMonitoring.ownBackendNotice') }}
      </Alert>

      <NeutralContainer v-if="!usage.usesOwnBackend" class="mb-6">
        <div class="flex items-center justify-between mb-2">
          <span class="font-medium">{{ t('storageMonitoring.totalUsage') }}</span>
          <span class="text-sm text-[var(--text-muted)]">{{ formatBytes(usage.totalBytes) }} / {{ formatBytes(usage.quotaBytes) }} ({{ usage.quotaUsedPercent }}%)</span>
        </div>
        <div class="bg-[var(--bg-muted)] rounded-full h-4 overflow-hidden">
          <div :class="barColor(usage.quotaUsedPercent)" :style="{width: Math.min(100, usage.quotaUsedPercent) + '%'}" class="h-full rounded-full transition-all"/>
        </div>
      </NeutralContainer>

      <NeutralContainer v-else class="mb-6">
        <div class="flex items-center justify-between">
          <span class="font-medium">{{ t('storageMonitoring.totalUsage') }}</span>
          <span class="text-sm text-[var(--text-muted)]">{{ formatBytes(usage.totalBytes) }}</span>
        </div>
      </NeutralContainer>

      <!-- Category chart -->
      <SectionHeader>{{ t('storageMonitoring.categoryBreakdown') }}</SectionHeader>
      <NeutralContainer class="mb-6">
        <VChart :option="chartOption" autoresize style="height: 300px"/>
      </NeutralContainer>

      <!-- Category table -->
      <SectionHeader>{{ t('storageMonitoring.categoryDetails') }}</SectionHeader>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
          <tr class="border-b border-[var(--border)]">
            <th class="text-left p-2">{{ t('storageMonitoring.category') }}</th>
            <th class="text-right p-2">{{ t('storageMonitoring.used') }}</th>
            <th class="text-right p-2">{{ t('storageMonitoring.quota') }}</th>
            <th class="text-right p-2">{{ t('storageMonitoring.files') }}</th>
            <th class="text-left p-2 min-w-[150px]">{{ t('storageMonitoring.usage') }}</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="cat in usage.categories" :key="cat.category" class="border-b border-[var(--border)]">
            <td class="p-2 font-medium">{{ categoryLabel(cat.category) }}</td>
            <td class="text-right p-2">{{ formatBytes(cat.totalBytes) }}</td>
            <td class="text-right p-2">{{ usage.categoryQuotas[cat.category] ? formatBytes(usage.categoryQuotas[cat.category]) : '—' }}</td>
            <td class="text-right p-2">{{ cat.fileCount }}</td>
            <td class="p-2">
              <div v-if="usage.categoryQuotas[cat.category]" class="bg-[var(--bg-muted)] rounded-full h-2 overflow-hidden">
                <div :class="barColor(cat.totalBytes * 100 / usage.categoryQuotas[cat.category])" :style="{width: Math.min(100, cat.totalBytes * 100 / usage.categoryQuotas[cat.category]) + '%'}" class="h-full rounded-full"/>
              </div>
              <span v-else class="text-xs text-[var(--text-muted)]">{{ t('storageMonitoring.trackedOnly') }}</span>
            </td>
          </tr>
          </tbody>
        </table>
      </div>

      <p class="text-xs text-[var(--text-muted)] mt-4">{{ t('storageMonitoring.readOnlyNote') }}</p>
    </template>
  </ViewContent>
</template>
