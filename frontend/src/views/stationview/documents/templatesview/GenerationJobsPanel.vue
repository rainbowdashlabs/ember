/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onBeforeUnmount, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {documentTemplates} from '@/api'
import type {GenerationJobSummary} from '@/api/generated/schema'
import {isRunning} from '@/components/documents/bulk/bulkGeneration'
import {describeFailure, type Failure} from '@/util/failure'
import GenerationJobRow from './GenerationJobRow.vue'

/**
 * The latest runs that generate a template for many members, newest first. The runs go on in the
 * background, so the list is read from the server: it survives a reload, and while a run is still
 * generating the list is read again every few seconds.
 */
const {t} = useI18n()

const POLL_MS = 3000

const jobs = ref<GenerationJobSummary[]>([])
const failure = ref<Failure | null>(null)
let timer: ReturnType<typeof setTimeout> | null = null

async function load() {
  try {
    jobs.value = await documentTemplates.listJobs()
    failure.value = null
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
  schedule()
}

function schedule() {
  if (timer) clearTimeout(timer)
  timer = jobs.value.some(isRunning) ? setTimeout(load, POLL_MS) : null
}

onMounted(load)
onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
  timer = null
})

defineExpose({reload: load})
</script>

<template>
  <NeutralContainer v-if="jobs.length > 0 || failure" class="space-y-3" data-testid="generation-jobs">
    <SubHeader>{{ t('documentTemplates.bulk.jobsTitle') }}</SubHeader>
    <FailureAlert :failure="failure"/>
    <GenerationJobRow v-for="job in jobs" :key="job.id" :job="job"/>
  </NeutralContainer>
</template>
