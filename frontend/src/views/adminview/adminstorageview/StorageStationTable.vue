/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import {isClusterOrigin} from '@/api/storageMonitoring'
import {STORAGE_CATEGORY_COLORS, buildStorageCategoryLabeler} from '@/util/storage'
import {useStorageCapabilities, type StorageRoomRow} from '@/composables/useStorageQuotas'
import StorageStationActions from './storagestationtable/StorageStationActions.vue'
import StorageStationName from './storagestationtable/StorageStationName.vue'
import StorageStationStatusBadge from './storagestationtable/StorageStationStatusBadge.vue'
import StorageUsageBar from './storagestationtable/StorageUsageBar.vue'
import {storageStatusOf} from './storagestationtable/storageStatus'
import {useStorageStationTable} from './storagestationtable/useStorageStationTable'

/**
 * Every station with what it keeps and what it may keep.
 *
 * <p>Writes nothing itself: an action travels up as an event and the screen decides what it means, which is
 * how the instance's listing and an association's picture of its own stations share one table.
 *
 * <p>A station an association governs is not the instance's to change: what the instance grants such a
 * station is the pool its association hands out of, and a number set here would change nothing anybody can see.
 */
const props = defineProps<{ stations: StorageRoomRow[] }>()

const emit = defineEmits<{
  recalculate: [stationId: string]
  reset: [stationId: string]
  edit: [stationId: string]
}>()

const {t} = useI18n()
const capabilities = useStorageCapabilities()

const table = useStorageStationTable(() => props.stations)

const categoryLabel = buildStorageCategoryLabeler(t)

function governedElsewhere(station: StorageRoomRow): boolean {
  return capabilities.deferToCluster && isClusterOrigin(station.origin)
}
</script>

<template>
  <div class="space-y-2">
    <div class="flex items-center justify-between gap-2">
      <SubHeader>{{ t('storageMonitoring.stationOverview') }}</SubHeader>
      <TableColumnPicker :table="table"/>
    </div>
    <RecordTable :table="table" row-test-id="storage-station-row" test-id="storage-station-table">
      <template #cell-name="{row}">
        <StorageStationName :governed-elsewhere="governedElsewhere(row)" :station="row"/>
      </template>
      <template #cell-usage="{row}">
        <StorageUsageBar :category-color-map="STORAGE_CATEGORY_COLORS" :category-label="categoryLabel" :station="row"/>
      </template>
      <template #cell-percent="{row, text}">
        <span :class="{'text-(--text-muted)': row.usesOwnBackend}" class="whitespace-nowrap">{{ text }}</span>
      </template>
      <template #cell-status="{row, text}">
        <StorageStationStatusBadge :label="text" :status="storageStatusOf(row)"/>
      </template>
      <template #cell-preset="{row, text}">
        <span v-if="!text" class="text-(--text-muted)">-</span>
        <span v-else :class="{'text-(--text-muted)': !row.presetName}">{{ text }}</span>
      </template>
      <template #actions-head>{{ t('storageMonitoring.actions') }}</template>
      <template #actions="{row}">
        <StorageStationActions
            :governed-elsewhere="governedElsewhere(row)"
            :station="row"
            @edit="emit('edit', $event)"
            @recalculate="emit('recalculate', $event)"
            @reset="emit('reset', $event)"
        />
      </template>
    </RecordTable>
  </div>
</template>
