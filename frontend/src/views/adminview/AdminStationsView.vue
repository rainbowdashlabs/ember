/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import type {AdminStationSummary} from '@/api/generated/schema'
import {stations, transfer} from '@/api'
import StationsGrid from './adminstationsview/StationsGrid.vue'
import StationsTable from './adminstationsview/StationsTable.vue'
import StationListControls from './adminstationsview/StationListControls.vue'
import StationImportModal from './adminstationsview/StationImportModal.vue'
import {StationFilters, StationViews} from './adminstationsview/stationList'
import {useStationList} from './adminstationsview/useStationList'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {useAsyncAction} from '@/composables/useAsyncAction'

const {t} = useI18n()
const router = useRouter()

const {shown, loading, failure: listFailure, reload: loadStations, filter, view, table, replace} = useStationList()

const nothingMatches = computed(() => filter.value !== StationFilters.ALL && shown.value.length === 0)

const {
  show: showDeleteModal,
  target: deleteTarget,
  requestDelete,
  confirm: confirmDelete,
} = useConfirmDelete<AdminStationSummary>({
  onDelete: s => stations.deleteStation(s.id),
  onSuccess: () => loadStations(),
  failure: listFailure,
})

function navigateToEdit(id: string) {
  router.push({name: 'admin-station-edit', params: {id}})
}

function navigateToCreate() {
  router.push({name: 'admin-station-edit'})
}

const importToken = ref('')
const showImportModal = ref(false)

const {running: importing, failure: importFailure, run: handleStartImport} = useAsyncAction(async () => {
  if (!importToken.value) return
  const result = await transfer.startImport(importToken.value)
  showImportModal.value = false
  router.push({name: 'admin-station-import', params: {stationUid: result.stationId}})
})

</script>

<template>
  <ViewContent :title="t('pages.admin-stations.title')" :subtitle="t('pages.admin-stations.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="listFailure"/>
      <FailureAlert :failure="importFailure"/>

      <template v-if="!loading">
        <StationListControls v-model:filter="filter" v-model:view="view">
          <TableColumnPicker v-if="view === StationViews.TABLE" :table="table"/>
        </StationListControls>

        <template v-if="view === StationViews.TILES">
          <StationsGrid
              :stations="shown"
              :on-reminded="replace"
              @import="showImportModal = true"
              @edit="navigateToEdit"
              @delete="requestDelete"/>
          <EmptyState v-if="nothingMatches" data-testid="stations-empty-filter">{{ t('adminStations.emptyFilter') }}</EmptyState>
        </template>

        <template v-else>
          <ButtonRow>
            <PrimaryButton :icon="['fas', 'plus']" @click="navigateToCreate">{{ t('adminStations.create') }}</PrimaryButton>
            <SecondaryButton :icon="['fas', 'upload']" @click="showImportModal = true">
              {{ t('adminStations.importStation') }}
            </SecondaryButton>
          </ButtonRow>
          <StationsTable :table="table" :on-reminded="replace" @edit="navigateToEdit" @delete="requestDelete"/>
        </template>
      </template>

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
