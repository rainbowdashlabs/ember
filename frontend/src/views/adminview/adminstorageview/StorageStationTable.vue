/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import {
  type AdminStationUsage,
  recalculateStation,
  resetStationQuotas,
} from '@/api/storageMonitoring'
import {STORAGE_CATEGORY_COLORS, buildStorageCategoryLabeler} from '@/util/storage'
import {byValue, useSortable} from '@/composables/useSortable'
import StorageStationHeader, {type StorageSortKey} from './storagestationtable/StorageStationHeader.vue'
import StorageStationRow from './storagestationtable/StorageStationRow.vue'

const props = defineProps<{ stations: AdminStationUsage[] }>()
const emit = defineEmits<{ reload: [] }>()

const {t} = useI18n()

const {sortKey, direction, sorted: sortedStations, toggle} = useSortable<AdminStationUsage, StorageSortKey>({
  items: () => props.stations,
  initialKey: 'percent',
  initialDirection: 'desc',
  comparators: {
    name: byValue(station => station.stationName),
    usage: byValue(station => station.totalBytes),
    percent: byValue(station => station.quotaUsedPercent),
  },
})

const categoryLabel = buildStorageCategoryLabeler(t)

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
    <SubHeader>{{ t('storageMonitoring.stationOverview') }}</SubHeader>
    <div class="overflow-x-auto">
      <table class="w-full text-sm">
        <StorageStationHeader :sort-key="sortKey" :direction="direction" @sort="toggle"/>
        <tbody>
        <StorageStationRow v-for="station in sortedStations" :key="station.stationId"
                           :station="station"
                           :category-color-map="STORAGE_CATEGORY_COLORS"
                           :category-label="categoryLabel"
                           @recalculate="handleRecalculate"
                           @reset="handleReset"/>
        </tbody>
      </table>
    </div>
  </div>
</template>
