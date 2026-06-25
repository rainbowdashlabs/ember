/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {Station} from '@/api/types'
import {stations, transfer} from '@/api'
import type {ImportProgress} from '@/api/transfer'
import StationsGrid from './adminstationsview/StationsGrid.vue'
import StationImportProgress from './adminstationsview/StationImportProgress.vue'
import StationImportModal from './adminstationsview/StationImportModal.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {t} = useI18n()
const router = useRouter()

const {config: stationList, loading, error, reload: loadStations} = useConfigPanel<Station[]>({
  initial: [],
  fetch: () => stations.listStations(),
})

const {
  show: showDeleteModal,
  target: deleteTarget,
  requestDelete,
  confirm: confirmDelete,
} = useConfirmDelete<Station>({
  onDelete: s => stations.deleteStation(s.id.toString()),
  onSuccess: () => loadStations(),
  error,
})

function navigateToCreate() {
  router.push({name: 'admin-station-edit'})
}

function navigateToEdit(id: string) {
  router.push({name: 'admin-station-edit', params: {id}})
}

const importSourceUrl = ref('')
const importToken = ref('')
const importProgress = ref<ImportProgress | null>(null)
const importing = ref(false)
const showImportModal = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null

async function handleStartImport() {
  if (!importSourceUrl.value || !importToken.value) return
  importing.value = true
  importProgress.value = null
  showImportModal.value = false
  error.value = ''
  try {
    const result = await transfer.startImport(importSourceUrl.value, importToken.value)
    await pollProgress(result.stationId)
  } catch {
    error.value = t('common.error')
    importing.value = false
  }
}

async function pollProgress(stationId: string) {
  pollTimer = setInterval(async () => {
    try {
      const progress = await transfer.getImportProgress(stationId)
      importProgress.value = progress
      if (progress.status === 'COMPLETED' || progress.status === 'FAILED') {
        if (pollTimer) clearInterval(pollTimer)
        pollTimer = null
        importing.value = false
        if (progress.status === 'COMPLETED') {
          await loadStations()
        }
      }
    } catch {
      if (pollTimer) clearInterval(pollTimer)
      pollTimer = null
      importing.value = false
      error.value = t('common.error')
    }
  }, 1000)
}

</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <StationsGrid
          v-if="!loading"
          :stations="stationList"
          @create="navigateToCreate"
          @import="showImportModal = true"
          @edit="navigateToEdit"
          @delete="requestDelete"/>

      <StationImportProgress v-if="importProgress" :progress="importProgress"/>

      <StationImportModal
          v-model="showImportModal"
          v-model:source-url="importSourceUrl"
          v-model:token="importToken"
          :importing="importing"
          @start="handleStartImport"/>

      <ConfirmDeleteModal
          v-model="showDeleteModal"
          :message="t('adminStations.deleteConfirm', {name: deleteTarget?.name})"
          @confirm="confirmDelete"/>
    </div>
  </ViewContent>
</template>
