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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {describeFailure} from '@/util/failure'
import Spinner from '@/components/feedback/Spinner.vue'
import StationForm from './adminstationeditview/StationForm.vue'
import type {ManagerDetail} from '@/api/stations'
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

/**
 * The station by its own name at the head of the page, because "Wache bearbeiten" stands above
 * every one of them and is what the tab, the history and a bookmark carry. The plain wording stands
 * while the station loads, where it could not be fetched, and for one being created.
 */
const pageTitle = computed(() => (isEdit.value && name.value.trim()
    ? name.value.trim()
    : t('pages.admin-station-edit.title')))

const {loading, failure} = useAsyncLoader(async () => {
  if (!stationId.value) return
  const detail = await stations.getStation(stationId.value)
  name.value = detail.name ?? ''
  manager.value = detail.manager ?? null
  managerEmail.value = ''
  editingManager.value = false
})

async function save() {
  failure.value = null
  try {
    const emailToSend = managerEmail.value.trim() || undefined
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
    failure.value = describeFailure(e, t)
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
  <ViewContent :title="pageTitle" :subtitle="t('pages.admin-station-edit.subtitle')">
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
        {{ t('adminStations.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>

      <FailureAlert :failure="failure"/>

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
