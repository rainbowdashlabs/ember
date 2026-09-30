/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {TrackingStatusName, TransferContext} from '@/api/dataTracking'
import SubHeader from '@/components/typography/SubHeader.vue'
import StatusBadge from './StatusBadge.vue'
import StatusReasonFields from './StatusReasonFields.vue'
import IgnoredColumnsField from './IgnoredColumnsField.vue'

defineProps<{
  statuses: TrackingStatusName[]
  columnOptions: { value: string; label: string; group?: string }[]
}>()

const transfer = defineModel<TransferContext>('transfer', {required: true})

const {t} = useI18n()
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
      <IgnoredColumnsField v-model:ignored="transfer.ignoredColumns" :column-options="columnOptions"/>
    </div>
  </section>
</template>
