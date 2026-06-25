/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {GdprExportContext, TrackingStatusName} from '@/api/dataTracking'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import StatusBadge from './StatusBadge.vue'
import StatusReasonFields from './StatusReasonFields.vue'

const props = defineProps<{
  exportCtx: GdprExportContext
  statuses: TrackingStatusName[]
  columnOptions: { value: string; label: string; group?: string }[]
}>()

const {t} = useI18n()

const ignoredColumns = computed({
  get: () => props.exportCtx.ignoredColumns ?? [],
  set: (v: string[]) => { props.exportCtx.ignoredColumns = [...v] },
})
</script>

<template>
  <section>
    <div class="flex items-center justify-between">
      <SectionHeader class="!text-base">{{ t('adminDataTracking.gdprExport') }}</SectionHeader>
      <StatusBadge :status="exportCtx.status"/>
    </div>
    <div class="space-y-2 mt-2">
      <StatusReasonFields
          v-model:status="exportCtx.status"
          v-model:reason="exportCtx.reason"
          :statuses="statuses"
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
