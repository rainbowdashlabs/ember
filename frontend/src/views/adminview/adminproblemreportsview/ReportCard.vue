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
import RecentRequestsTable from './RecentRequestsTable.vue'
import type {ProblemReport} from '@/api/problemReports'
import type {RequestHistoryEntry} from '@/api/client'

const props = defineProps<{
  report: ProblemReport
  expanded: boolean
}>()

const emit = defineEmits<{
  toggle: [id: number]
  ack: [id: number]
  remove: [id: number]
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
        <IconButton v-if="!report.acknowledged" :icon="['fas', 'check']" :label="t('problemReport.acknowledge')"
                    class="text-success hover:bg-success/15" @click.stop="emit('ack', report.id)"/>
        <DeleteButton @click.stop="emit('remove', report.id)"/>
      </template>
    </ReportCardHeader>

    <template v-if="expanded">
      <ReportMetadata :report="report"/>
      <RecentRequestsTable v-if="recentRequests.length > 0" :requests="recentRequests"/>
    </template>
  </NeutralContainer>
</template>
