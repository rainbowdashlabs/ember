/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ReportCardHeader from '@/components/problem/ReportCardHeader.vue'
import ReportMetadata from './ReportMetadata.vue'
import RecentRequestsTable from '@/components/problem/RecentRequestsTable.vue'
import AuthImage from '@/components/display/AuthImage.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import type {ProblemReport} from '@/api/problemReports'
import type {RequestHistoryEntry} from '@/api/client'

const props = defineProps<{
  report: ProblemReport
  expanded: boolean
  /** Whether this instance reports to a beacon at all, which is what makes passing one on possible. */
  canForward: boolean
  /**
   * Whether this one has gone to the beacon, is waiting for somebody here to send it, or neither.
   *
   * <p>A report carrying a picture waits where the operator asked to see pictures before they leave,
   * and a waiting one used to look exactly like one that had gone. That is how a report being held
   * reads as a report being lost.
   */
  forwardState?: 'sent' | 'held' | null
}>()

const emit = defineEmits<{
  toggle: [id: number]
  ack: [id: number]
  remove: [id: number]
  forward: [id: number]
}>()

const {t} = useI18n()

const recentRequests = computed<RequestHistoryEntry[]>(() => {
  if (!props.report.recentRequests) return []
  try {
    return JSON.parse(props.report.recentRequests)
  } catch {
    return []
  }
})

</script>

<template>
  <NeutralContainer class="space-y-2" data-testid="problem-report">
    <ReportCardHeader
        :acknowledged="report.acknowledged"
        :expanded="expanded"
        :message="report.message"
        :reported-at="report.createdAt"
        :reporter="report.reporterName"
        @click="emit('toggle', report.id)"
    >
      <template #actions>
        <InfoBadge v-if="forwardState === 'held'" data-testid="problem-report-held">
          {{ t('problemReport.heldForReview') }}
        </InfoBadge>
        <SuccessBadge v-else-if="forwardState === 'sent'" data-testid="problem-report-sent">
          {{ t('problemReport.forwarded') }}
        </SuccessBadge>
        <IconButton v-if="canForward" :icon="['fas', 'tower-broadcast']" :label="t('problemReport.forward')"
                    data-testid="problem-report-forward" @click.stop="emit('forward', report.id)"/>
        <IconButton v-if="!report.acknowledged" :icon="['fas', 'check']" :label="t('problemReport.acknowledge')"
                    class="text-success hover:bg-success/15" @click.stop="emit('ack', report.id)"/>
        <DeleteButton @click.stop="emit('remove', report.id)"/>
      </template>
    </ReportCardHeader>

    <template v-if="expanded">
      <ReportMetadata :report="report"/>
      <AuthImage
          v-if="report.screenshotFileId"
          :src="`/admin/problem-reports/${report.id}/screenshot`"
          class="w-full rounded-theme border border-bg-light-accent dark:border-bg-dark-accent"
          data-testid="report-screenshot"
      />
      <RecentRequestsTable v-if="recentRequests.length > 0" :requests="recentRequests"/>
    </template>
  </NeutralContainer>
</template>
