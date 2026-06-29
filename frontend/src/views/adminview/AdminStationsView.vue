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
import StationsGrid from './adminstationsview/StationsGrid.vue'
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

const importToken = ref('')
const importing = ref(false)
const showImportModal = ref(false)

async function handleStartImport() {
  if (!importToken.value) return
  importing.value = true
  error.value = ''
  try {
    const result = await transfer.startImport(importToken.value)
    showImportModal.value = false
    router.push({name: 'admin-station-import', params: {stationUid: result.stationId}})
  } catch {
    error.value = t('common.error')
  } finally {
    importing.value = false
  }
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

      <StationImportModal
          v-model="showImportModal"
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
