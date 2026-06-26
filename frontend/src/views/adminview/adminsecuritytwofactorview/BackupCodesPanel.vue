/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
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
import { useConfigPanel } from '@/composables/useConfigPanel'

const { t } = useI18n()

const { config, loading, error, runWith } = useConfigPanel<BackupCodesConfig>({
  initial: { count: 10 },
  fetch: () => adminSettings.getBackupCodesConfig(),
})

async function save() {
  await runWith(() => adminSettings.updateBackupCodesConfig(config.value), { rethrow: true })
}
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
