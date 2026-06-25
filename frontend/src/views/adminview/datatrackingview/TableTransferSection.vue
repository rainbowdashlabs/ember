/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {TrackingStatusName, TransferContext} from '@/api/dataTracking'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import StatusBadge from './StatusBadge.vue'
import StatusReasonFields from './StatusReasonFields.vue'

const props = defineProps<{
  transfer: TransferContext
  statuses: TrackingStatusName[]
  columnOptions: { value: string; label: string; group?: string }[]
}>()

const {t} = useI18n()

const ignoredColumns = computed({
  get: () => props.transfer.ignoredColumns ?? [],
  set: (v: string[]) => { props.transfer.ignoredColumns = [...v] },
})
</script>

<template>
  <section>
    <div class="flex items-center justify-between">
      <SectionHeader class="!text-base">{{ t('adminDataTracking.stationTransfer') }}</SectionHeader>
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
        <label class="block text-xs text-(--text-muted) mb-1">
          {{ t('adminDataTracking.detail.ignoredColumns') }}
        </label>
        <MultiSelectDropdown
            v-model="ignoredColumns"
            :options="columnOptions"
            :placeholder="t('adminDataTracking.detail.ignoredColumnsPlaceholder')"
            searchable
        />
      </div>
    </div>
  </section>
</template>
