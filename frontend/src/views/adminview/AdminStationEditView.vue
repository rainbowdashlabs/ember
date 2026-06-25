/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import StationForm from './adminstationeditview/StationForm.vue'
import type {ManagerDetail} from '@/api/types'
import {stations} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const stationId = computed(() => {
  const id = route.params.id
  return id ? String(id) : null
})
const isEdit = computed(() => stationId.value !== null)

const name = ref('')
const manager = ref<ManagerDetail | null>(null)
const managerEmail = ref('')
const editingManager = ref(false)

const {loading, error} = useAsyncLoader(async () => {
  if (!stationId.value) return
  const detail = await stations.getStation(stationId.value)
  name.value = detail.name ?? ''
  manager.value = detail.manager ?? null
  managerEmail.value = ''
  editingManager.value = false
})

async function save() {
  error.value = ''
  try {
    const emailToSend = editingManager.value ? managerEmail.value : undefined
    if (isEdit.value) {
      const detail = await stations.updateStation(stationId.value!, {name: name.value, managerEmail: emailToSend})
      manager.value = detail.manager ?? null
      editingManager.value = false
      managerEmail.value = ''
    } else {
      const created = await stations.createStation({name: name.value, managerEmail: managerEmail.value || undefined})
      await router.replace({name: 'admin-station-edit', params: {id: created.id}})
    }
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

function startTransfer() {
  editingManager.value = true
  managerEmail.value = ''
}

function cancelTransfer() {
  editingManager.value = false
  managerEmail.value = ''
}

function goBack() {
  router.push({name: 'admin-stations'})
}
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
        {{ t('adminStations.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <StationForm
        v-if="!loading"
        v-model:name="name"
        v-model:manager-email="managerEmail"
        :manager="manager"
        :editing-manager="editingManager"
        :is-edit="isEdit"
        :save="save"
        @start-transfer="startTransfer"
        @cancel-transfer="cancelTransfer"
      />
    </div>
  </ViewContent>
</template>
