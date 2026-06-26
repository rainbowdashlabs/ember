/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import { adminSettings } from '@/api'
import type { TwoFactorCoreConfigResponse } from '@/api/adminSettings'
import { useConfigPanel } from '@/composables/useConfigPanel'

const { t } = useI18n()

const generating = ref(false)
const { config, loading, error, runWith } = useConfigPanel<TwoFactorCoreConfigResponse>({
  initial: {
    enabled: true,
    stepUpFreshnessSeconds: 300,
    trustedDeviceMaxDays: 30,
    enrollmentGraceDays: 7,
    secretKeyConfigured: false,
  },
  fetch: () => adminSettings.getTwoFactorCoreConfig(),
})

async function save() {
  await runWith(
    () => adminSettings.updateTwoFactorCoreConfig({
      enabled: config.value.enabled,
      stepUpFreshnessSeconds: config.value.stepUpFreshnessSeconds,
      trustedDeviceMaxDays: config.value.trustedDeviceMaxDays,
      enrollmentGraceDays: config.value.enrollmentGraceDays,
    }),
    { rethrow: true },
  )
}

async function generateKey() {
  await runWith(() => adminSettings.generateTwoFactorSecretKey(), { busy: generating })
}
</script>

<template>
  <div class="space-y-6">
    <Spinner v-if="loading" size="md"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <template v-if="!loading">
      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('adminSecurity.twoFactor.title') }}</SectionHeader>
        <MutedText tag="p" size="sm">{{ t('adminSecurity.twoFactor.hint') }}</MutedText>

        <div class="flex items-center justify-between">
          <div>
            <div class="font-medium">{{ t('adminSecurity.twoFactor.enabled') }}</div>
            <MutedText tag="div" size="sm">{{ t('adminSecurity.twoFactor.enabledHint') }}</MutedText>
          </div>
          <ToggleInput v-model="config.enabled"/>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <FieldLabel class="mb-1">{{ t('adminSecurity.twoFactor.stepUpFreshnessSeconds') }}</FieldLabel>
            <NumberInput v-model="config.stepUpFreshnessSeconds"/>
            <MutedText tag="div" class="mt-1" size="sm">
              {{ t('adminSecurity.twoFactor.stepUpFreshnessSecondsHint') }}
            </MutedText>
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('adminSecurity.twoFactor.trustedDeviceMaxDays') }}</FieldLabel>
            <NumberInput v-model="config.trustedDeviceMaxDays"/>
            <MutedText tag="div" class="mt-1" size="sm">
              {{ t('adminSecurity.twoFactor.trustedDeviceMaxDaysHint') }}
            </MutedText>
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('adminSecurity.twoFactor.enrollmentGraceDays') }}</FieldLabel>
            <NumberInput v-model="config.enrollmentGraceDays"/>
            <MutedText tag="div" class="mt-1" size="sm">
              {{ t('adminSecurity.twoFactor.enrollmentGraceDaysHint') }}
            </MutedText>
          </div>
        </div>

        <div class="flex justify-end">
          <SaveButton :action="save"/>
        </div>
      </NeutralContainer>

      <NeutralContainer class="space-y-3">
        <SubHeader>{{ t('adminSecurity.twoFactor.secretKey.title') }}</SubHeader>
        <MutedText tag="p" size="sm">{{ t('adminSecurity.twoFactor.secretKey.hint') }}</MutedText>

        <div class="flex items-center justify-between gap-3">
          <div>
            <SuccessBadge v-if="config.secretKeyConfigured">
              {{ t('adminSecurity.twoFactor.secretKey.configured') }}
            </SuccessBadge>
            <ErrorBadge v-else>
              {{ t('adminSecurity.twoFactor.secretKey.missing') }}
            </ErrorBadge>
          </div>
          <PrimaryButton v-if="!config.secretKeyConfigured" :disabled="generating" @click="generateKey">
            {{ generating ? t('common.loading') : t('adminSecurity.twoFactor.secretKey.generate') }}
          </PrimaryButton>
        </div>

        <Alert variant="info">{{ t('adminSecurity.twoFactor.secretKey.restartWarning') }}</Alert>
      </NeutralContainer>
    </template>
  </div>
</template>
