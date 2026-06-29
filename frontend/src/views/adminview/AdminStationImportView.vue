/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ImportProgressChecklist from '@/components/transfer/ImportProgressChecklist.vue'
import {transfer} from '@/api'
import type {ImportProgress} from '@/api/transfer'

const route = useRoute()
const router = useRouter()
const {t} = useI18n()

const stationUid = (route.params.stationUid as string) ?? ''
const progress = ref<ImportProgress | null>(null)
const error = ref('')
let pollTimer: ReturnType<typeof setInterval> | null = null

async function tick() {
  try {
    progress.value = await transfer.getImportProgress(stationUid)
    if (progress.value.status === 'COMPLETED' || progress.value.status === 'FAILED') {
      stopPolling()
    }
  } catch {
    error.value = t('common.error')
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
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <PageHeader>{{ t('adminStationImport.title') }}</PageHeader>
        <SecondaryButton @click="backToStations">
          {{ t('adminStationImport.backToStations') }}
        </SecondaryButton>
      </div>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <ImportProgressChecklist v-if="progress" :progress="progress"/>
    </div>
  </ViewContent>
</template>
