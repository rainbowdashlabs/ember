/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {describeFailure, type Failure} from '@/util/failure'
import ImportProgressChecklist from '@/components/transfer/ImportProgressChecklist.vue'
import {transfer} from '@/api'
import type {ImportProgress} from '@/api/transfer'

const route = useRoute()
const router = useRouter()
const {t} = useI18n()

const stationUid = (route.params.stationUid as string) ?? ''
const progress = ref<ImportProgress | null>(null)
const failure = ref<Failure | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

/**
 * Which station is coming in, at the head of the page, so two imports watched side by side are told
 * apart in the tab and in the history. The plain wording stands until the first progress report has
 * arrived, and where none can be fetched at all.
 */
const pageTitle = computed(() => (progress.value?.stationName
    ? t('pages.admin-station-import.titleNamed', {name: progress.value.stationName})
    : t('pages.admin-station-import.title')))

async function tick() {
  try {
    progress.value = await transfer.getImportProgress(stationUid)
    if (progress.value.status === 'COMPLETED' || progress.value.status === 'FAILED') {
      stopPolling()
    }
  } catch (e) {
    failure.value = describeFailure(e, t)
    stopPolling()
  }
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(async () => {
  await tick()
  pollTimer = setInterval(tick, 1000)
})

onBeforeUnmount(() => {
  stopPolling()
})

function backToStations() {
  router.push({name: 'admin-stations'})
}
</script>

<template>
  <ViewContent :title="pageTitle" :subtitle="t('pages.admin-station-import.subtitle')">
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SecondaryButton @click="backToStations">
          {{ t('adminStationImport.backToStations') }}
        </SecondaryButton>
      </div>
      <FailureAlert :failure="failure"/>
      <ImportProgressChecklist v-if="progress" :progress="progress"/>
    </div>
  </ViewContent>
</template>
