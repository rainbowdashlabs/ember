/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type {LendingRequestDetail} from '@/api/lending'

defineProps<{
  detail: LendingRequestDetail
}>()

const {t} = useI18n()

function formatDate(d: string | null): string {
  if (!d) return '-'
  return new Date(d).toLocaleDateString('de-DE')
}
</script>

<template>
  <NeutralContainer class="mb-4">
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 text-sm">
      <div><span class="font-medium">{{ t('lending.requestingStation') }}:</span> {{ detail.request.requestingStationName }}</div>
      <div><span class="font-medium">{{ t('lending.owningStation') }}:</span> {{ detail.request.owningStationName }}</div>
      <div><span class="font-medium">{{ t('lending.dateFrom') }}:</span> {{ formatDate(detail.request.request.requestedDateFrom) }}</div>
      <div><span class="font-medium">{{ t('lending.dateTo') }}:</span> {{ formatDate(detail.request.request.requestedDateTo) }}</div>
    </div>
  </NeutralContainer>
</template>
