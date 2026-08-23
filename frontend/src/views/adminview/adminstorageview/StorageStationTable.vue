/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import {STORAGE_CATEGORY_COLORS, buildStorageCategoryLabeler} from '@/util/storage'
import {byValue, useSortable} from '@/composables/useSortable'
import type {StorageRoomRow} from '@/composables/useStorageQuotas'
import StorageStationHeader, {type StorageSortKey} from './storagestationtable/StorageStationHeader.vue'
import StorageStationRow from './storagestationtable/StorageStationRow.vue'

/**
 * Every station with what it keeps and what it may keep.
 *
 * <p>Writes nothing itself: an action travels up as an event and the screen decides what it means, which is
 * how the instance's listing and an association's picture of its own stations share one table.
 */
const props = defineProps<{ stations: StorageRoomRow[] }>()

const emit = defineEmits<{
  recalculate: [stationId: string]
  reset: [stationId: string]
  edit: [stationId: string]
}>()

const {t} = useI18n()

const {sortKey, direction, sorted: sortedStations, toggle} = useSortable<StorageRoomRow, StorageSortKey>({
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
</script>

<template>
  <div>
    <SubHeader>{{ t('storageMonitoring.stationOverview') }}</SubHeader>
    <div class="overflow-x-auto">
      <table class="w-full text-sm">
        <StorageStationHeader :direction="direction" :sort-key="sortKey" @sort="toggle"/>
        <tbody>
        <StorageStationRow v-for="station in sortedStations" :key="station.stationId"
                           :category-color-map="STORAGE_CATEGORY_COLORS"
                           :category-label="categoryLabel"
                           :station="station"
                           @edit="emit('edit', $event)"
                           @recalculate="emit('recalculate', $event)"
                           @reset="emit('reset', $event)"/>
        </tbody>
      </table>
    </div>
  </div>
</template>
