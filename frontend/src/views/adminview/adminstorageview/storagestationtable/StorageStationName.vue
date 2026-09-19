/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type {StorageRoomRow} from '@/composables/useStorageQuotas'

/** A station's name, with a badge where its room is an association's store, its own backend or not the instance's to set. */
defineProps<{
  station: StorageRoomRow
  governedElsewhere: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <span class="font-medium">
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
  </span>
</template>
