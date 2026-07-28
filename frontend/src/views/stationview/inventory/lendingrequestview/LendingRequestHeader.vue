/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import {LendingStatus, type LendingRequestDetail, type LendingStatusName} from '@/api/lending'

defineProps<{
  detail: LendingRequestDetail
}>()

const {t} = useI18n()

function statusBadge(status: LendingStatusName) {
  switch (status) {
    case LendingStatus.APPROVED:
    case LendingStatus.LENT:
      return 'success'
    case LendingStatus.DECLINED:
      return 'error'
    case LendingStatus.REQUESTED:
    case LendingStatus.RETURNED:
      return 'secondary'
    case LendingStatus.CLOSED:
      return 'info'
    default:
      return 'secondary'
  }
}
</script>

<template>
  <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-4">
    <SubHeader>
      {{ detail.request.isOwner ? detail.request.requestingStationName : detail.request.owningStationName }}
    </SubHeader>
    <div class="flex items-center gap-2">
      <SuccessBadge v-if="statusBadge(detail.request.request.status) === 'success'">{{ t(`lending.status.${detail.request.request.status}`) }}</SuccessBadge>
      <ErrorBadge v-else-if="statusBadge(detail.request.request.status) === 'error'">{{ t(`lending.status.${detail.request.request.status}`) }}</ErrorBadge>
      <InfoBadge v-else-if="statusBadge(detail.request.request.status) === 'info'">{{ t(`lending.status.${detail.request.request.status}`) }}</InfoBadge>
      <SecondaryBadge v-else>{{ t(`lending.status.${detail.request.request.status}`) }}</SecondaryBadge>
      <ErrorBadge v-if="detail.request.overdue">{{ t('lending.overdue') }}</ErrorBadge>
    </div>
  </div>
</template>
