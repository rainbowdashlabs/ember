/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IconButton from '@/components/button/IconButton.vue'
import ReportCardHeader from '@/components/problem/ReportCardHeader.vue'
import ReportMetadataGrid from '@/components/problem/ReportMetadataGrid.vue'
import RecentRequestsTable from '@/components/problem/RecentRequestsTable.vue'
import type {BeaconReport} from '@/api/beacon'
import type {RequestHistoryEntry} from '@/api/client'

/**
 * Somebody's own words, forwarded without their name, read exactly like a report written here.
 *
 * <p>The name on it is the operator's of the instance it came from, which is who can be written to
 * about it. The page is shown as text and never as a link: it arrives from elsewhere.
 */
const props = defineProps<{
  report: BeaconReport
  expanded: boolean
}>()

const emit = defineEmits<{
  toggle: [id: number]
  acknowledge: [id: number]
}>()

const {t} = useI18n()

const reporter = computed(() => props.report.contactName?.trim() || t('beacon.unknownInstance'))

const metadata = computed(() => [
  {label: t('problemReport.page'), value: props.report.page},
  {label: t('beacon.version'), value: props.report.version},
  {label: t('problemReport.browser'), value: props.report.browser},
  {label: t('problemReport.screen'), value: props.report.screenSize},
  {label: t('problemReport.roles'), value: props.report.roles},
  {label: t('beacon.contact'), value: props.report.contactMail},
].filter((entry): entry is {label: string; value: string} => !!entry.value))

/**
 * The calls the screen made before somebody wrote, as the screen recorded them.
 *
 * <p>Read as the table this instance draws for its own reports, so a report forwarded from elsewhere
 * is read the same way as one written here. What cannot be read is left as the text it arrived as
 * rather than dropped: a beacon takes what an older instance sends it.
 */
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
  <NeutralContainer class="space-y-2" data-testid="beacon-report">
    <ReportCardHeader
        :acknowledged="report.acknowledged"
        :expanded="expanded"
        :message="report.message"
        :reported-at="report.reportedAt"
        :reporter="reporter"
        @click="emit('toggle', report.id)"
    >
      <template #actions>
        <IconButton v-if="!report.acknowledged" :icon="['fas', 'check']" :label="t('problemReport.acknowledge')"
                    class="text-success hover:bg-success/15" @click.stop="emit('acknowledge', report.id)"/>
      </template>
    </ReportCardHeader>

    <ReportMetadataGrid v-if="expanded && metadata.length > 0" :entries="metadata"/>
    <RecentRequestsTable v-if="expanded && recentRequests.length > 0" :requests="recentRequests"/>
  </NeutralContainer>
</template>
