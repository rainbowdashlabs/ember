/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ProgressBar from '@/components/feedback/ProgressBar.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import BareButton from '@/components/button/BareButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {documentTemplates} from '@/api'
import type {GenerationJobResponse, GenerationJobSummary} from '@/api/generated/schema'
import {doneOf, isRunning} from '@/components/documents/bulk/bulkGeneration'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {formatDateTime} from '@/util/format'
import JobFailureList from './JobFailureList.vue'

/**
 * One run: its template, who started it and when, how far it got, and on request the members whose
 * document could not be filed, each with the reason.
 */
const props = defineProps<{
  job: GenerationJobSummary
}>()

const {t} = useI18n()

const opened = ref(false)
const detail = ref<GenerationJobResponse | null>(null)
const running = computed(() => isRunning(props.job))

const reading = useAsyncAction(async () => {
  detail.value = await documentTemplates.getJob(props.job.id)
})

function toggle() {
  opened.value = !opened.value
  if (opened.value) void reading.run()
}

watch([() => props.job.failed, running], () => {
  if (opened.value) void reading.run()
})
</script>

<template>
  <div class="space-y-2 border-t border-(--border) pt-3 first-of-type:border-t-0 first-of-type:pt-0" data-testid="generation-job">
    <div class="flex flex-wrap items-center gap-2">
      <span class="font-semibold">{{ job.templateName }}</span>
      <MutedText size="sm">{{ t('documentTemplates.bulk.startedBy', {name: job.startedByName, date: formatDateTime(job.startedAt)}) }}</MutedText>
      <InfoBadge v-if="running">{{ t('documentTemplates.bulk.running') }}</InfoBadge>
      <SuccessBadge>{{ t('documentTemplates.bulk.filed', {count: job.filed}) }}</SuccessBadge>
      <ErrorBadge v-if="job.failed > 0">{{ t('documentTemplates.bulk.failed', {count: job.failed}) }}</ErrorBadge>
    </div>
    <ProgressBar :value="doneOf(job)" :max="Math.max(1, job.total)" :variant="job.failed > 0 ? 'error' : 'success'"/>
    <BareButton v-if="job.failed > 0" class="text-sm underline" data-testid="generation-job-toggle" @click="toggle">
      {{ opened ? t('documentTemplates.bulk.hideFailures') : t('documentTemplates.bulk.showFailures') }}
    </BareButton>
    <FailureAlert :failure="reading.failure.value"/>
    <JobFailureList v-if="opened && detail" :members="detail.members"/>
  </div>
</template>
