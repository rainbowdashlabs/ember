/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import BeaconReportCard from './BeaconReportCard.vue'
import type {BeaconReport} from '@/api/beacon'

/**
 * The problem reports of the installations that report here, read the way this instance's own are.
 */
defineProps<{
  reports: BeaconReport[]
  showAcknowledged: boolean
}>()

const emit = defineEmits<{
  toggleAcknowledged: []
  acknowledge: [id: number]
}>()

const {t} = useI18n()

const expandedId = ref<number | null>(null)

function toggleExpand(id: number) {
  expandedId.value = expandedId.value === id ? null : id
}

function acknowledge(id: number) {
  emit('acknowledge', id)
}
</script>

<template>
  <div class="space-y-4">
    <label class="flex items-center gap-2 text-sm">
      <ToggleInput
          :aria-label="t('problemReport.showAcknowledged')"
          :model-value="showAcknowledged"
          @update:model-value="emit('toggleAcknowledged')"
      />
      {{ t('problemReport.showAcknowledged') }}
    </label>

    <EmptyState v-if="reports.length === 0" :message="t('beacon.noReports')"/>

    <BeaconReportCard
        v-for="report in reports"
        :key="report.id"
        :expanded="expandedId === report.id"
        :report="report"
        @acknowledge="acknowledge"
        @toggle="toggleExpand"
    />
  </div>
</template>
