/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import type {Station} from '@/api/types'
import {stations} from '@/api'
import {transfer} from '@/api'
import type {ImportProgress} from '@/api/transfer'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()
const router = useRouter()

const stationList = ref<Station[]>([])
const loading = ref(true)
const error = ref('')

const showDeleteModal = ref(false)
const deleteTarget = ref<Station | null>(null)

async function loadStations() {
  loading.value = true
  error.value = ''
  try {
    stationList.value = await stations.listStations()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function navigateToCreate() {
  router.push({name: 'admin-station-edit'})
}

function navigateToEdit(id: string) {
  router.push({name: 'admin-station-edit', params: {id}})
}

function requestDelete(station: Station) {
  deleteTarget.value = station
  showDeleteModal.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await stations.deleteStation(deleteTarget.value.id.toString())
    showDeleteModal.value = false
    deleteTarget.value = null
    await loadStations()
  } catch {
    error.value = t('common.error')
  }
}

// -- Import --
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

onMounted(loadStations)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <div v-if="!loading" class="grid gap-4 sm:grid-cols-2">
        <!-- Add station tile -->
        <PrimaryContainer
            class="flex flex-col items-center justify-center gap-3 cursor-pointer py-6 border-dashed hover:opacity-80 transition-opacity"
            @click="navigateToCreate"
        >
          <font-awesome-icon :icon="['fas', 'plus']" class="text-2xl"/>
          <span class="font-medium">{{ t('adminStations.create') }}</span>
        </PrimaryContainer>

        <!-- Import station tile -->
        <NeutralContainer
            class="flex flex-col items-center justify-center gap-3 cursor-pointer py-6 hover:opacity-80 transition-opacity"
            @click="showImportModal = true"
        >
          <font-awesome-icon :icon="['fas', 'upload']" class="text-2xl text-(--text-muted)"/>
          <span class="font-medium">{{ t('adminStations.importStation') }}</span>
        </NeutralContainer>

        <!-- Station tiles -->
        <NeutralContainer v-for="station in stationList" :key="station.id"
                          class="flex items-center justify-between py-6">
          <span class="font-medium text-lg">{{ station.name }}</span>
          <div class="flex items-center gap-2">
            <EditButton @click="navigateToEdit(station.id.toString())"/>
            <DeleteButton @click="requestDelete(station)"/>
          </div>
        </NeutralContainer>
      </div>

      <!-- Import progress -->
      <NeutralContainer v-if="importProgress" class="space-y-3">
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium">
            {{ importProgress.stationName }}
          </span>
          <span class="text-sm text-(--text-muted)">
            {{ importProgress.completedTables }} / {{ importProgress.totalTables }}
          </span>
        </div>
        <div class="w-full bg-bg-light-accent dark:bg-bg-dark-accent rounded-full h-2.5">
          <div
              class="h-2.5 rounded-full transition-all duration-300"
              :class="importProgress.status === 'FAILED' ? 'bg-error' : 'bg-primary'"
              :style="{ width: `${(importProgress.completedTables / importProgress.totalTables) * 100}%` }"
          />
        </div>
        <Alert v-if="importProgress.status === 'COMPLETED'" variant="success">
          {{ t('adminStations.importCompleted') }}
        </Alert>
        <Alert v-if="importProgress.status === 'FAILED'" variant="error">
          {{ t('adminStations.importFailed', { error: importProgress.error ?? '' }) }}
        </Alert>
        <p v-if="importProgress.status === 'IN_PROGRESS' && importProgress.currentTable" class="text-xs text-(--text-muted)">
          {{ t('adminStations.importProgress', { table: importProgress.currentTable, completed: importProgress.completedTables, total: importProgress.totalTables }) }}
        </p>
      </NeutralContainer>

      <!-- Import modal -->
      <Modal v-model="showImportModal">
        <div class="space-y-4">
          <p class="text-sm text-(--text-muted)">{{ t('adminStations.importHint') }}</p>
          <div class="space-y-1">
            <FieldLabel>{{ t('adminStations.importSourceUrl') }}</FieldLabel>
            <TextInput v-model="importSourceUrl" :placeholder="t('adminStations.importSourceUrlPlaceholder')"/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('adminStations.importToken') }}</FieldLabel>
            <TextInput v-model="importToken" :placeholder="t('adminStations.importTokenPlaceholder')"/>
          </div>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showImportModal = false">{{ t('adminStations.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="importing || !importSourceUrl || !importToken" @click="handleStartImport">
              {{ importing ? t('adminStations.importStarting') : t('adminStations.importStart') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Delete modal -->
      <Modal v-model="showDeleteModal">
        <div class="space-y-4">
          <p>{{ t('adminStations.deleteConfirm', {name: deleteTarget?.name}) }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showDeleteModal = false">{{ t('adminStations.cancel') }}</SecondaryButton>
            <ErrorButton @click="confirmDelete">{{ t('adminStations.delete') }}</ErrorButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
