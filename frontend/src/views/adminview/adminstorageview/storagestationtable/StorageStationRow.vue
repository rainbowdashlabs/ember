/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import {type AdminStationUsage} from '@/api/storageMonitoring'
import {formatBytes} from '@/util/storage'
import StorageUsageBar from './StorageUsageBar.vue'
import StorageStationStatusBadge from './StorageStationStatusBadge.vue'

defineProps<{
  station: AdminStationUsage
  categoryColorMap: Record<string, string>
  categoryLabel: (cat: string) => string
}>()

const emit = defineEmits<{
  recalculate: [uid: string]
  reset: [uid: string]
}>()

const {t} = useI18n()
</script>

<template>
  <tr class="border-b border-(--border) hover:bg-(--bg-hover)">
    <td class="p-2 font-medium">
      {{ station.stationName }}
      <InfoBadge v-if="station.usesOwnBackend" class="ml-2 text-[10px]">{{ t('storageMonitoring.ownBackendBadge') }}</InfoBadge>
    </td>
    <td class="p-2">
      <StorageUsageBar :station="station" :category-color-map="categoryColorMap" :category-label="categoryLabel"/>
    </td>
    <td class="text-right p-2 whitespace-nowrap">
      <span v-if="!station.usesOwnBackend">{{ station.quotaUsedPercent }}% / {{ formatBytes(station.quotaBytes) }}</span>
      <span v-else class="text-(--text-muted)">—</span>
    </td>
    <td class="text-center p-2">
      <StorageStationStatusBadge :uses-own-backend="station.usesOwnBackend" :quota-used-percent="station.quotaUsedPercent"/>
    </td>
    <td class="p-2 text-sm">
      <span v-if="station.usesOwnBackend" class="text-(--text-muted)">—</span>
      <span v-else-if="station.presetName">{{ station.presetName }}</span>
      <span v-else class="text-(--text-muted)">{{ t('storageMonitoring.defaultQuota') }}</span>
    </td>
    <td class="text-right p-2">
      <div class="flex gap-1 justify-end">
        <SecondaryButton @click="emit('recalculate', station.stationId)">
          <font-awesome-icon :icon="['fas', 'arrows-rotate']"/>
        </SecondaryButton>
        <SecondaryButton @click="emit('reset', station.stationId)">
          <font-awesome-icon :icon="['fas', 'rotate-left']"/>
        </SecondaryButton>
      </div>
    </td>
  </tr>
</template>
