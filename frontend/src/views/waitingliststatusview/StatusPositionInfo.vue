/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import StatusFieldRow from './StatusFieldRow.vue'
import WaitingListStatusBadge from '@/components/badge/WaitingListStatusBadge.vue'
import type { WaitingListPublicStatus } from '@/api/types'
import { formatDate } from '@/util/format'

const props = defineProps<{
  status: WaitingListPublicStatus
}>()

const { t } = useI18n()

function nextConfirmationDate(): string {
  if (!props.status?.confirmedAt || !props.status.confirmIntervalDays) return '-'
  const next = new Date(props.status.confirmedAt)
  next.setDate(next.getDate() + props.status.confirmIntervalDays)
  return formatDate(next.toISOString())
}
</script>

<template>
  <StatusFieldRow :label="t('waitingList.status')">
    <WaitingListStatusBadge :status="props.status.status" class="ml-1" />
  </StatusFieldRow>
  <StatusFieldRow v-if="props.status.status === 'WAITING'" wide :label="t('waitingList.position')">
    <span class="ml-1 font-medium text-lg">{{ props.status.position }}</span>
    <p class="text-xs text-(--text-muted) mt-1">{{ t('waitingList.publicStatus.positionHint') }}</p>
  </StatusFieldRow>
  <StatusFieldRow :label="t('waitingList.publicStatus.waitingSince')" :value="formatDate(props.status.createdAt) || '-'" />
  <StatusFieldRow :label="t('waitingList.publicStatus.lastConfirmation')" :value="formatDate(props.status.confirmedAt) || '-'" />
  <StatusFieldRow
    v-if="props.status.confirmIntervalDays > 0"
    wide
    :label="t('waitingList.publicStatus.nextConfirmation')"
    :value="nextConfirmationDate()"
  />
</template>
