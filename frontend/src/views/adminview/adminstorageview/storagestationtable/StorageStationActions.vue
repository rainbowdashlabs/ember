/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import type {StorageRoomRow} from '@/composables/useStorageQuotas'
import {useStorageCapabilities} from '@/composables/useStorageQuotas'

/**
 * What can be done to one station's room: grant it more, count its bytes again, or put it back on
 * the defaults. A station an association governs cannot be put back from here.
 */
defineProps<{
  station: StorageRoomRow
  governedElsewhere: boolean
}>()

const emit = defineEmits<{
  recalculate: [stationId: string]
  reset: [stationId: string]
  edit: [stationId: string]
}>()

const {t} = useI18n()
const capabilities = useStorageCapabilities()
</script>

<template>
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
</template>
