/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import { adminSettings } from '@/api'
import type { BackupCodesConfig } from '@/api/adminSettings'

const { t } = useI18n()

const loading = ref(true)
const error = ref('')
const config = ref<BackupCodesConfig>({ count: 10 })

async function load() {
  loading.value = true
  error.value = ''
  try {
    config.value = await adminSettings.getBackupCodesConfig()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  error.value = ''
  try {
    config.value = await adminSettings.updateBackupCodesConfig(config.value)
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

onMounted(load)
</script>

<template>
  <div>
    <Spinner v-if="loading" size="md"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <NeutralContainer v-if="!loading" class="space-y-4">
      <SectionHeader>{{ t('adminSecurity.backupCodes.title') }}</SectionHeader>
      <MutedText tag="p" size="sm">{{ t('adminSecurity.backupCodes.hint') }}</MutedText>

      <div>
        <FieldLabel class="mb-1">{{ t('adminSecurity.backupCodes.count') }}</FieldLabel>
        <NumberInput v-model="config.count"/>
        <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.backupCodes.countHint') }}</MutedText>
      </div>

      <div class="flex justify-end">
        <SaveButton :action="save"/>
      </div>
    </NeutralContainer>
  </div>
</template>
