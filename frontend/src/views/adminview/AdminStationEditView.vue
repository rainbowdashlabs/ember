/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {ManagerDetail} from '@/api/types'
import {stations} from '@/api'

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
const loading = ref(false)
const saving = ref(false)
const error = ref('')

const hasManager = computed(() => manager.value !== null)

async function loadStation() {
  if (!stationId.value) return
  loading.value = true
  error.value = ''
  try {
    const detail = await stations.getStation(stationId.value)
    name.value = detail.name ?? ''
    manager.value = detail.manager ?? null
    managerEmail.value = ''
    editingManager.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
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
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
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

onMounted(loadStation)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SecondaryButton @click="goBack">
        <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2"/>
        {{ t('adminStations.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer v-if="!loading" class="space-y-4">
        <SectionHeader>{{ isEdit ? t('adminStations.editTitle') : t('adminStations.createTitle') }}</SectionHeader>

        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('adminStations.name') }}</label>
          <TextInput v-model="name" :placeholder="t('adminStations.namePlaceholder')"/>
        </div>

        <!-- Manager section -->
        <div class="space-y-2">
          <label class="block text-sm font-medium">{{ t('adminStations.managerEmail') }}</label>

          <!-- Show manager state when one exists and not editing -->
          <template v-if="isEdit && hasManager && !editingManager">
            <component :is="manager!.accountReady ? SuccessContainer : InfoContainer"
                       class="flex items-center justify-between">
              <div>
                <div class="font-medium">
                  {{
                    manager!.firstName || manager!.lastName ? `${manager!.firstName ?? ''} ${manager!.lastName ?? ''}`.trim() : manager!.email
                  }}
                </div>
                <div class="text-sm text-(--text-muted)">{{ manager!.email }}</div>
                <div class="text-xs mt-1">
                  <span v-if="manager!.accountReady" class="text-success">
                    <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>{{ t('adminStations.accountReady') }}
                  </span>
                  <span v-else class="text-info">
                    <font-awesome-icon :icon="['fas', 'clock-rotate-left']"
                                       class="mr-1"/>{{ t('adminStations.accountPending') }}
                  </span>
                </div>
              </div>
              <EditButton @click="startTransfer"/>
            </component>
          </template>

          <!-- Email input for creating or transferring -->
          <template v-if="!isEdit || !hasManager || editingManager">
            <TextInput v-model="managerEmail" :placeholder="t('adminStations.managerEmailPlaceholder')"/>
            <p class="text-xs text-(--text-muted)">{{ t('adminStations.managerEmailHint') }}</p>
            <SecondaryButton v-if="editingManager" @click="cancelTransfer">
              {{ t('adminStations.cancelTransfer') }}
            </SecondaryButton>
          </template>
        </div>

        <PrimaryButton :disabled="saving || !name" @click="save">
          {{ saving ? t('common.loading') : (isEdit ? t('adminStations.save') : t('adminStations.createSubmit')) }}
        </PrimaryButton>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
