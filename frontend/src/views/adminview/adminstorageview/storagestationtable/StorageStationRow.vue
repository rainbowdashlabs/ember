/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import {isClusterOrigin} from '@/api/storageMonitoring'
import type {StorageRoomRow} from '@/composables/useStorageQuotas'
import {useStorageCapabilities} from '@/composables/useStorageQuotas'
import {formatBytes} from '@/util/storage'
import StorageUsageBar from './StorageUsageBar.vue'
import StorageStationStatusBadge from './StorageStationStatusBadge.vue'

const props = defineProps<{
  station: StorageRoomRow
  categoryColorMap: Record<string, string>
  categoryLabel: (cat: string) => string
}>()

const emit = defineEmits<{
  recalculate: [stationId: string]
  reset: [stationId: string]
  edit: [stationId: string]
}>()

const {t} = useI18n()
const capabilities = useStorageCapabilities()

/**
 * A station an association governs is not the instance's to change: what the instance grants such a station
 * is the pool its association hands out of, and a number set here would change nothing anybody can see.
 */
const governedElsewhere = computed(
    () => capabilities.deferToCluster && isClusterOrigin(props.station.origin))
</script>

<template>
  <tr class="border-b border-(--border) hover:bg-(--bg-hover)" data-testid="storage-station-row">
    <td class="p-2 font-medium">
      {{ station.stationName }}
      <InfoBadge v-if="station.ownStore" class="ml-2 text-[10px]">
        {{ t('storageMonitoring.ownStoreBadge') }}
      </InfoBadge>
      <InfoBadge v-if="station.usesOwnBackend" class="ml-2 text-[10px]">
        {{ t('storageMonitoring.ownBackendBadge') }}
      </InfoBadge>
      <InfoBadge v-else-if="governedElsewhere" class="ml-2 text-[10px]">
        {{ t('storageMonitoring.clusterGovernedBadge') }}
      </InfoBadge>
    </td>
    <td class="p-2">
      <StorageUsageBar :category-color-map="categoryColorMap" :category-label="categoryLabel" :station="station"/>
    </td>
    <td class="text-right p-2 whitespace-nowrap">
      <span v-if="!station.usesOwnBackend">{{ station.quotaUsedPercent }}% / {{ formatBytes(station.quotaBytes) }}</span>
      <span v-else class="text-(--text-muted)">-</span>
    </td>
    <td class="text-center p-2">
      <StorageStationStatusBadge :quota-used-percent="station.quotaUsedPercent"
                                 :uses-own-backend="station.usesOwnBackend"/>
    </td>
    <td class="p-2 text-sm">
      <span v-if="station.usesOwnBackend" class="text-(--text-muted)">-</span>
      <span v-else-if="station.presetName">{{ station.presetName }}</span>
      <span v-else-if="capabilities.showsOrigin && station.origin" class="text-(--text-muted)">
        {{ t(`storageMonitoring.origin.${station.origin}`) }}
      </span>
      <span v-else class="text-(--text-muted)">{{ t('storageMonitoring.defaultQuota') }}</span>
    </td>
    <td class="text-right p-2">
      <div class="flex gap-1 justify-end">
        <EditButton v-if="!capabilities.deferToCluster"
                    :label="t('clusterStorage.grantRoom')"
                    data-testid="station-room-edit"
                    @click="emit('edit', station.stationId)"/>
        <SecondaryButton v-if="capabilities.canRecalculate" @click="emit('recalculate', station.stationId)">
          <font-awesome-icon :icon="['fas', 'arrows-rotate']"/>
        </SecondaryButton>
        <SecondaryButton :disabled="governedElsewhere"
                         :title="governedElsewhere ? t('storageMonitoring.clusterGovernedHint') : undefined"
                         data-testid="station-room-reset"
                         @click="emit('reset', station.stationId)">
          <font-awesome-icon :icon="['fas', 'rotate-left']"/>
        </SecondaryButton>
      </div>
    </td>
  </tr>
</template>
