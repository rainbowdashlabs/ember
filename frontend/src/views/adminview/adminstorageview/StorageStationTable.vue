/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import {
  type AdminStationUsage,
  formatBytes,
  recalculateStation,
  resetStationQuotas,
} from '@/api/storageMonitoring'

const props = defineProps<{ stations: AdminStationUsage[] }>()
const emit = defineEmits<{ reload: [] }>()

const {t} = useI18n()

const sortBy = ref<'name' | 'usage' | 'percent'>('percent')
const sortDesc = ref(true)

const sortedStations = computed(() => {
  return [...props.stations].sort((a, b) => {
    let cmp = 0
    if (sortBy.value === 'name') cmp = a.stationName.localeCompare(b.stationName)
    else if (sortBy.value === 'usage') cmp = a.totalBytes - b.totalBytes
    else cmp = a.quotaUsedPercent - b.quotaUsedPercent
    return sortDesc.value ? -cmp : cmp
  })
})

const categoryColorMap: Record<string, string> = {
  KB_FILES: '#3694FF',
  BOARD_ATTACHMENTS: '#FF6421',
  PAGE_FILES: '#00C507',
  IMAGE_LOST_AND_FOUND: '#73CEFF',
  IMAGE_QUIZ_QUESTION: '#FFDD1B',
  IMAGE_KB_ICON: '#C71100',
  IMAGE_KB_IMAGE: '#3694FF',
  IMAGE_LOGO_FRAGMENT: '#9333EA',
}

function statusBadge(percent: number) {
  if (percent >= 95) return 'full'
  if (percent >= 80) return 'warning'
  return 'ok'
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

function toggleSort(field: 'name' | 'usage' | 'percent') {
  if (sortBy.value === field) sortDesc.value = !sortDesc.value
  else { sortBy.value = field; sortDesc.value = true }
}

async function handleRecalculate(uid: string) {
  await recalculateStation(uid)
  emit('reload')
}

async function handleReset(uid: string) {
  await resetStationQuotas(uid)
  emit('reload')
}
</script>

<template>
  <div>
    <SectionHeader>{{ t('storageMonitoring.stationOverview') }}</SectionHeader>
    <div class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
        <tr class="border-b border-(--border)">
          <th class="text-left p-2 cursor-pointer" @click="toggleSort('name')">
            {{ t('storageMonitoring.stationName') }}
            <font-awesome-icon v-if="sortBy === 'name'" :icon="['fas', sortDesc ? 'sort-down' : 'sort-up']" class="ml-1"/>
          </th>
          <th class="text-left p-2 cursor-pointer min-w-50" @click="toggleSort('usage')">
            {{ t('storageMonitoring.usage') }}
            <font-awesome-icon v-if="sortBy === 'usage'" :icon="['fas', sortDesc ? 'sort-down' : 'sort-up']" class="ml-1"/>
          </th>
          <th class="text-right p-2 cursor-pointer" @click="toggleSort('percent')">
            {{ t('storageMonitoring.quota') }}
            <font-awesome-icon v-if="sortBy === 'percent'" :icon="['fas', sortDesc ? 'sort-down' : 'sort-up']" class="ml-1"/>
          </th>
          <th class="text-center p-2">{{ t('storageMonitoring.status') }}</th>
          <th class="text-left p-2">{{ t('storageMonitoring.preset') }}</th>
          <th class="text-right p-2">{{ t('storageMonitoring.actions') }}</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="station in sortedStations" :key="station.stationId" class="border-b border-(--border) hover:bg-(--bg-hover)">
          <td class="p-2 font-medium">
            {{ station.stationName }}
            <InfoBadge v-if="station.usesOwnBackend" class="ml-2 text-[10px]">{{ t('storageMonitoring.ownBackendBadge') }}</InfoBadge>
          </td>
          <td class="p-2">
            <div v-if="!station.usesOwnBackend" class="flex items-center gap-2">
              <div class="flex-1 bg-(--bg-muted) rounded-full h-3 overflow-hidden flex">
                <div v-for="cat in station.categories.filter(c => c.category !== 'IMAGE_AVATAR' && c.totalBytes > 0)" :key="cat.category"
                     :style="{width: (station.quotaBytes > 0 ? cat.totalBytes / station.quotaBytes * 100 : 0) + '%', backgroundColor: categoryColorMap[cat.category] || '#9ca3af'}"
                     :title="categoryLabel(cat.category) + ': ' + formatBytes(cat.totalBytes)"
                     class="h-full first:rounded-l-full last:rounded-r-full"/>
              </div>
              <span class="text-xs whitespace-nowrap text-(--text-muted)">{{ formatBytes(station.totalBytes) }}</span>
            </div>
            <span v-else class="text-xs text-(--text-muted)">{{ formatBytes(station.totalBytes) }}</span>
          </td>
          <td class="text-right p-2 whitespace-nowrap">
            <span v-if="!station.usesOwnBackend">{{ station.quotaUsedPercent }}% / {{ formatBytes(station.quotaBytes) }}</span>
            <span v-else class="text-(--text-muted)">—</span>
          </td>
          <td class="text-center p-2">
            <span v-if="station.usesOwnBackend" class="text-xs text-(--text-muted)">—</span>
            <SuccessBadge v-else-if="statusBadge(station.quotaUsedPercent) === 'ok'">OK</SuccessBadge>
            <InfoBadge v-else-if="statusBadge(station.quotaUsedPercent) === 'warning'">{{ t('storageMonitoring.warning') }}</InfoBadge>
            <ErrorBadge v-else>{{ t('storageMonitoring.full') }}</ErrorBadge>
          </td>
          <td class="p-2 text-sm">
            <span v-if="station.usesOwnBackend" class="text-(--text-muted)">—</span>
            <span v-else-if="station.presetName">{{ station.presetName }}</span>
            <span v-else class="text-(--text-muted)">{{ t('storageMonitoring.defaultQuota') }}</span>
          </td>
          <td class="text-right p-2">
            <div class="flex gap-1 justify-end">
              <SecondaryButton @click="handleRecalculate(station.stationId)">
                <font-awesome-icon :icon="['fas', 'arrows-rotate']"/>
              </SecondaryButton>
              <SecondaryButton @click="handleReset(station.stationId)">
                <font-awesome-icon :icon="['fas', 'rotate-left']"/>
              </SecondaryButton>
            </div>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
