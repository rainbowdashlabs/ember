/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import { adminSettings } from '@/api'
import type { HibpConfig } from '@/api/adminSettings'
import { useConfigPanel } from '@/composables/useConfigPanel'

const { t } = useI18n()

const {config, loading, error, runWith} = useConfigPanel<HibpConfig>({
  initial: {
    enabled: true,
    endpoint: 'https://api.pwnedpasswords.com/range/',
    staleAfterDays: 30,
    timeoutSeconds: 5,
  },
  fetch: () => adminSettings.getHibpConfig(),
})

async function save() {
  await runWith(() => adminSettings.updateHibpConfig(config.value), {rethrow: true})
}
</script>

<template>
  <ViewContent :title="t('pages.admin-security-hibp.title')" :subtitle="t('pages.admin-security-hibp.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <NeutralContainer class="space-y-4">
          <MutedText tag="p" size="sm">{{ t('adminSecurity.hibp.hint') }}</MutedText>

          <div class="flex items-center justify-between">
            <div>
              <div class="font-medium">{{ t('adminSecurity.hibp.enabled') }}</div>
              <MutedText tag="div" size="sm">{{ t('adminSecurity.hibp.enabledHint') }}</MutedText>
            </div>
            <ToggleInput v-model="config.enabled" :aria-label="t('adminSecurity.hibp.enabled')"/>
          </div>

          <div>
            <FieldLabel class="mb-1">{{ t('adminSecurity.hibp.endpoint') }}</FieldLabel>
            <TextInput v-model="config.endpoint"/>
            <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.hibp.endpointHint') }}</MutedText>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <FieldLabel class="mb-1">{{ t('adminSecurity.hibp.staleAfterDays') }}</FieldLabel>
              <NumberInput v-model="config.staleAfterDays"/>
              <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.hibp.staleAfterDaysHint') }}</MutedText>
            </div>
            <div>
              <FieldLabel class="mb-1">{{ t('adminSecurity.hibp.timeoutSeconds') }}</FieldLabel>
              <NumberInput v-model="config.timeoutSeconds"/>
              <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.hibp.timeoutSecondsHint') }}</MutedText>
            </div>
          </div>

          <div class="flex justify-end">
            <SaveButton :action="save"/>
          </div>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
