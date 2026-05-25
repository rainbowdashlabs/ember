/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import {stationManage} from '@/api'
import type {StationImportProgress} from '@/api/stationManage'

const emit = defineEmits<{
  error: [msg: string]
  success: [msg: string]
}>()

const {t} = useI18n()

const importSourceUrl = ref('')
const importTokenInput = ref('')
const importingStation = ref(false)
const showImportConfirm = ref(false)
const importProgress = ref<StationImportProgress | null>(null)
let importPollTimer: ReturnType<typeof setInterval> | null = null

function requestStationImport() {
  if (!importSourceUrl.value || !importTokenInput.value) return
  showImportConfirm.value = true
}

async function startStationImport() {
  showImportConfirm.value = false
  importingStation.value = true
  importProgress.value = null
  try {
    await stationManage.importStation(importSourceUrl.value, importTokenInput.value)
    pollStationImport()
  } catch {
    emit('error', t('common.error'))
    importingStation.value = false
  }
}

function pollStationImport() {
  importPollTimer = setInterval(async () => {
    try {
      const progress = await stationManage.getImportProgress()
      importProgress.value = progress
      if (progress.status === 'COMPLETED' || progress.status === 'FAILED') {
        if (importPollTimer) clearInterval(importPollTimer)
        importPollTimer = null
        importingStation.value = false
      }
    } catch {
      if (importPollTimer) clearInterval(importPollTimer)
      importPollTimer = null
      importingStation.value = false
    }
  }, 1000)
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('stationManage.importTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('stationManage.importHint') }}</p>
    <div class="grid gap-4 sm:grid-cols-2">
      <div class="space-y-1">
        <FieldLabel>{{ t('stationManage.importSourceUrl') }}</FieldLabel>
        <TextInput v-model="importSourceUrl" :placeholder="t('stationManage.importSourceUrlPlaceholder')" />
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('stationManage.importToken') }}</FieldLabel>
        <TextInput v-model="importTokenInput" :placeholder="t('stationManage.importTokenPlaceholder')" />
      </div>
    </div>
    <PrimaryButton :disabled="importingStation || !importSourceUrl || !importTokenInput" @click="requestStationImport">
      {{ importingStation ? t('stationManage.importStarting') : t('stationManage.importStart') }}
    </PrimaryButton>

    <!-- Import progress -->
    <div v-if="importProgress" class="space-y-2">
      <div class="flex items-center justify-between text-sm">
        <span>{{ importProgress.completedTables }} / {{ importProgress.totalTables }}</span>
      </div>
      <div class="w-full bg-bg-light-accent dark:bg-bg-dark-accent rounded-full h-2">
        <div
            class="h-2 rounded-full transition-all duration-300"
            :class="importProgress.status === 'FAILED' ? 'bg-error' : 'bg-primary'"
            :style="{ width: `${(importProgress.completedTables / importProgress.totalTables) * 100}%` }"
        />
      </div>
      <Alert v-if="importProgress.status === 'COMPLETED'" variant="success">{{ t('stationManage.importCompleted') }}</Alert>
      <Alert v-if="importProgress.status === 'FAILED'" variant="error">{{ t('stationManage.importFailed', {error: importProgress.error ?? ''}) }}</Alert>
      <p v-if="importProgress.status === 'IN_PROGRESS' && importProgress.currentTable" class="text-xs text-(--text-muted)">
        {{ t('stationManage.importProgress', {table: importProgress.currentTable, completed: importProgress.completedTables, total: importProgress.totalTables}) }}
      </p>
    </div>
  </NeutralContainer>

  <!-- Import confirmation modal -->
  <Modal v-model="showImportConfirm">
    <div class="space-y-4">
      <SectionHeader>{{ t('stationManage.importConfirmTitle') }}</SectionHeader>
      <p class="text-sm">{{ t('stationManage.importConfirmText') }}</p>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="showImportConfirm = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton @click="startStationImport">{{ t('stationManage.importConfirmAction') }}</ErrorButton>
      </div>
    </div>
  </Modal>
</template>
