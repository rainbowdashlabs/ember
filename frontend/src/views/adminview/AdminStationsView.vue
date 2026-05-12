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

function navigateToEdit(id: number) {
  router.push({name: 'admin-station-edit', params: {id}})
}

function requestDelete(station: Station) {
  deleteTarget.value = station
  showDeleteModal.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await stations.deleteStation(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    await loadStations()
  } catch {
    error.value = t('common.error')
  }
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

        <!-- Station tiles -->
        <NeutralContainer v-for="station in stationList" :key="station.id"
                          class="flex items-center justify-between py-6">
          <span class="font-medium text-lg">{{ station.name }}</span>
          <div class="flex items-center gap-2">
            <EditButton @click="navigateToEdit(station.id)"/>
            <DeleteButton @click="requestDelete(station)"/>
          </div>
        </NeutralContainer>
      </div>

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
