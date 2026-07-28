/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ReportMetadata from './ReportMetadata.vue'
import RecentRequestsTable from './RecentRequestsTable.vue'
import type {ProblemReport} from '@/api/problemReports'
import type {RequestHistoryEntry} from '@/api/client'
import {formatDateTime} from '@/util/format'

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
  <NeutralContainer class="space-y-2">
    <div class="flex items-start justify-between gap-3 cursor-pointer" @click="emit('toggle', report.id)">
      <div class="flex-1">
        <div class="flex items-center gap-2 mb-1">
          <span class="text-sm font-semibold">{{ report.reporterName }}</span>
          <SuccessBadge v-if="report.acknowledged">{{ t('problemReport.acknowledged') }}</SuccessBadge>
          <MutedText size="xs">{{ formatDateTime(report.createdAt) }}</MutedText>
        </div>
        <p class="text-sm">{{ report.message }}</p>
      </div>
      <div class="flex items-center gap-1 shrink-0">
        <IconButton v-if="!report.acknowledged" :icon="['fas', 'check']" :label="t('problemReport.acknowledge')"
                    class="text-success hover:bg-success/15" @click.stop="emit('ack', report.id)"/>
        <DeleteButton @click.stop="emit('remove', report.id)"/>
        <font-awesome-icon :icon="['fas', expanded ? 'chevron-up' : 'chevron-down']"
                           class="h-3 w-3 text-(--text-muted) ml-1"/>
      </div>
    </div>

    <template v-if="expanded">
      <ReportMetadata :report="report"/>
      <RecentRequestsTable v-if="recentRequests.length > 0" :requests="recentRequests"/>
    </template>
  </NeutralContainer>
</template>
