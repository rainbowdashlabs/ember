/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {ClusterStationQuota} from '@/api/clusterGovernance'

const props = defineProps<{
  station: ClusterStationQuota
  busy: boolean
}>()

const emit = defineEmits<{
  save: [stationUid: string, quotaBytes: number | null]
}>()

const {t} = useI18n()

/** Bytes are what the backend deals in; whole gibibytes are what a person types. */
const GIB = 1024 * 1024 * 1024

const gibibytes = ref<number | undefined>(toGib(props.station.quotaBytes))

watch(() => props.station.quotaBytes, value => {
  gibibytes.value = toGib(value)
})

function toGib(bytes: number | null | undefined): number | undefined {
  return bytes === null || bytes === undefined ? undefined : Math.round((bytes / GIB) * 100) / 100
}

function save() {
  emit('save', props.station.stationUid, gibibytes.value == null ? null : Math.round(gibibytes.value * GIB))
}
</script>

<template>
  <NeutralContainer class="flex flex-wrap items-center justify-between gap-3" data-testid="station-quota">
    <span class="font-medium">{{ station.stationName }}</span>
    <div class="flex items-center gap-2">
      <NumberInput v-model="gibibytes" data-testid="station-quota-input"
                   :placeholder="t('clusterStorage.instanceDefault')" class="w-32"/>
      <span class="text-sm text-(--text-muted)">GiB</span>
      <SecondaryButton :disabled="busy" data-testid="station-quota-save" @click="save">{{ t('common.save') }}</SecondaryButton>
    </div>
  </NeutralContainer>
</template>
