/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {GdprExportContext, TrackingStatusName} from '@/api/dataTracking'
import SubHeader from '@/components/typography/SubHeader.vue'
import StatusBadge from './StatusBadge.vue'
import StatusReasonFields from './StatusReasonFields.vue'
import IgnoredColumnsField from './IgnoredColumnsField.vue'

defineProps<{
  statuses: TrackingStatusName[]
  columnOptions: { value: string; label: string; group?: string }[]
}>()

const exportCtx = defineModel<GdprExportContext>('exportCtx', {required: true})

const {t} = useI18n()
</script>

<template>
  <section>
    <div class="flex items-center justify-between">
      <SubHeader class="!text-base">{{ t('adminDataTracking.gdprExport') }}</SubHeader>
      <StatusBadge :status="exportCtx.status"/>
    </div>
    <div class="space-y-2 mt-2">
      <StatusReasonFields
          v-model:status="exportCtx.status"
          v-model:reason="exportCtx.reason"
          :statuses="statuses"
      />
      <IgnoredColumnsField v-model:ignored="exportCtx.ignoredColumns" :column-options="columnOptions"/>
      <div v-if="exportCtx.identityColumns?.length">
        <span class="text-xs text-(--text-muted)">{{ t('adminDataTracking.detail.identityColumns') }}:</span>
        <ul class="text-xs font-mono mt-1 space-y-0.5">
          <li v-for="ic in exportCtx.identityColumns" :key="ic.column">
            {{ ic.column }} <span class="text-(--text-muted)">({{ ic.type }})</span>
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>
