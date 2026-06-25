/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {TrackingStatusName} from '@/api/dataTracking'

type BatchContext = 'stationTransfer' | 'gdprExport' | 'gdprDeletion'

defineProps<{
  selectedCount: number
  batchSaving: boolean
}>()

const batchContext = defineModel<BatchContext>('batchContext', {required: true})
const batchStatus = defineModel<TrackingStatusName>('batchStatus', {required: true})

const emit = defineEmits<{
  apply: []
  selectAll: []
  clear: []
}>()

const {t} = useI18n()
</script>

<template>
  <div
      v-if="selectedCount > 0"
      class="sticky top-0 z-10 mb-3 p-3 rounded-theme border border-(--accent)/40 bg-(--accent)/10 flex items-center gap-2 flex-wrap"
  >
    <span class="text-sm font-semibold">
      {{ t('adminDataTracking.batch.selected', {n: selectedCount}) }}
    </span>
    <span class="text-xs text-(--text-muted)">{{ t('adminDataTracking.batch.setStatusOn') }}</span>
    <SelectInput v-model="batchContext" class="!w-auto">
      <option value="stationTransfer">{{ t('adminDataTracking.stationTransfer') }}</option>
      <option value="gdprExport">{{ t('adminDataTracking.gdprExport') }}</option>
      <option value="gdprDeletion">{{ t('adminDataTracking.gdprDeletion') }}</option>
    </SelectInput>
    <SelectInput v-model="batchStatus" class="!w-auto">
      <option value="TRACKED">TRACKED</option>
      <option value="IGNORED">IGNORED</option>
      <option value="UNVERIFIED">UNVERIFIED</option>
    </SelectInput>
    <PrimaryButton :disabled="batchSaving" @click="emit('apply')">
      <font-awesome-icon v-if="batchSaving" :icon="['fas', 'spinner']" class="animate-spin mr-1"/>
      {{ t('adminDataTracking.batch.apply') }}
    </PrimaryButton>
    <SecondaryButton @click="emit('selectAll')">
      {{ t('adminDataTracking.batch.selectAllFiltered') }}
    </SecondaryButton>
    <SecondaryButton @click="emit('clear')">
      {{ t('adminDataTracking.batch.clear') }}
    </SecondaryButton>
  </div>
</template>
