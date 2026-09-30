/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import type {TrackingStatusName, TransferContext} from '@/api/dataTracking'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import StatusBadge from './StatusBadge.vue'
import StatusReasonFields from './StatusReasonFields.vue'

defineProps<{
  statuses: TrackingStatusName[]
  columnOptions: { value: string; label: string; group?: string }[]
}>()

const transfer = defineModel<TransferContext>('transfer', {required: true})

const {t} = useI18n()
const ignoredColumnsId = useId()

const ignoredColumns = computed({
  get: () => transfer.value.ignoredColumns ?? [],
  set: (v: string[]) => { transfer.value = {...transfer.value, ignoredColumns: [...v]} },
})
</script>

<template>
  <section>
    <div class="flex items-center justify-between">
      <SubHeader class="!text-base">{{ t('adminDataTracking.stationTransfer') }}</SubHeader>
      <StatusBadge :status="transfer.status"/>
    </div>
    <div class="space-y-2 mt-2">
      <StatusReasonFields
          v-model:status="transfer.status"
          v-model:reason="transfer.reason"
          v-model:rationale="transfer.rationale"
          :statuses="statuses"
          show-rationale-on-tracked
      />
      <div>
        <label :for="ignoredColumnsId" class="block text-xs text-(--text-muted) mb-1">
          {{ t('adminDataTracking.detail.ignoredColumns') }}
        </label>
        <MultiSelectDropdown
            :id="ignoredColumnsId"
            v-model="ignoredColumns"
            :options="columnOptions"
            :placeholder="t('adminDataTracking.detail.ignoredColumnsPlaceholder')"
            searchable
        />
      </div>
    </div>
  </section>
</template>
