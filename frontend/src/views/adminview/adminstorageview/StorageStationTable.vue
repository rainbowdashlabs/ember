/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {
  type AdminStationUsage,
  recalculateStation,
  resetStationQuotas,
} from '@/api/storageMonitoring'
import {STORAGE_CATEGORY_COLORS, buildStorageCategoryLabeler} from '@/util/storage'
import StorageStationHeader from './storagestationtable/StorageStationHeader.vue'
import StorageStationRow from './storagestationtable/StorageStationRow.vue'

const props = defineProps<{ stations: AdminStationUsage[] }>()
const emit = defineEmits<{ reload: [] }>()

const {t} = useI18n()

type SortField = 'name' | 'usage' | 'percent'
const sortBy = ref<SortField>('percent')
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

const categoryLabel = buildStorageCategoryLabeler(t)

function toggleSort(field: SortField) {
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
        <StorageStationHeader :sort-by="sortBy" :sort-desc="sortDesc" @toggle="toggleSort"/>
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
