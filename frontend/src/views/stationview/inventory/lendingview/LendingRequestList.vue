/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import {formatDate} from '@/util/format'
import {LendingStatus, type LendingRequestResponse, type LendingStatusName} from '@/api/lending'

/**
 * Renders one lending request list. `direction` decides which side of the exchange is named on the
 * tile: incoming lists show the requesting station, outgoing lists the owning station.
 */
const props = defineProps<{
  entries: LendingRequestResponse[]
  direction: 'incoming' | 'outgoing'
}>()

const router = useRouter()
const {t} = useI18n()

function stationName(entry: LendingRequestResponse) {
  return props.direction === 'incoming' ? entry.requestingStationName : entry.owningStationName
}

function statusBadge(status: LendingStatusName) {
  switch (status) {
    case LendingStatus.APPROVED:
    case LendingStatus.LENT:
      return 'success'
    case LendingStatus.DECLINED:
      return 'error'
    case LendingStatus.CLOSED:
      return 'info'
    default:
      return 'secondary'
  }
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <NeutralContainer
        v-for="entry in entries"
        :key="entry.request.id"
        class="cursor-pointer hover:border-primary transition-colors"
        @click="router.push({name: 'inventory-lending-request', params: {id: entry.request.id}})"
    >
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div class="flex flex-col gap-0.5">
          <div>
            <span class="font-medium">{{ stationName(entry) }}</span>
            <span class="text-sm text-[var(--text-muted)] ml-2">
              {{ formatDate(entry.request.requestedDateFrom) || '-' }}
              <template v-if="entry.request.requestedDateTo"> - {{ formatDate(entry.request.requestedDateTo) }}</template>
            </span>
          </div>
          <span v-if="entry.itemSummary" class="text-xs text-[var(--text-muted)]">{{ entry.itemSummary }}</span>
        </div>
        <div class="flex items-center gap-2">
          <SuccessBadge v-if="statusBadge(entry.request.status) === 'success'">{{ t(`lending.status.${entry.request.status}`) }}</SuccessBadge>
          <ErrorBadge v-else-if="statusBadge(entry.request.status) === 'error'">{{ t(`lending.status.${entry.request.status}`) }}</ErrorBadge>
          <InfoBadge v-else-if="statusBadge(entry.request.status) === 'info'">{{ t(`lending.status.${entry.request.status}`) }}</InfoBadge>
          <SecondaryBadge v-else>{{ t(`lending.status.${entry.request.status}`) }}</SecondaryBadge>
          <ErrorBadge v-if="entry.overdue">{{ t('lending.overdue') }}</ErrorBadge>
        </div>
      </div>
    </NeutralContainer>
  </div>
</template>
