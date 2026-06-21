/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import { adminSettings } from '@/api'
import type { TokensConfigResponse } from '@/api/adminSettings'

const { t } = useI18n()

const loading = ref(true)
const error = ref('')
const generating = ref(false)
const config = ref<TokensConfigResponse>({
  tokenBytes: 32,
  verifyTokenHours: 24,
  passwordTokenHours: 72,
  sessionMinutes: 30,
  tokenPepperConfigured: false,
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    config.value = await adminSettings.getTokensConfig()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  error.value = ''
  try {
    config.value = await adminSettings.updateTokensConfig({
      tokenBytes: config.value.tokenBytes,
      verifyTokenHours: config.value.verifyTokenHours,
      passwordTokenHours: config.value.passwordTokenHours,
      sessionMinutes: config.value.sessionMinutes,
    })
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

async function generatePepper() {
  error.value = ''
  generating.value = true
  try {
    config.value = await adminSettings.generateTokenPepper()
  } catch {
    error.value = t('common.error')
  } finally {
    generating.value = false
  }
}

onMounted(load)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('adminSecurity.tokens.title') }}</SectionHeader>
          <MutedText tag="p" size="sm">{{ t('adminSecurity.tokens.hint') }}</MutedText>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <FieldLabel class="mb-1">{{ t('adminSecurity.tokens.tokenBytes') }}</FieldLabel>
              <NumberInput v-model="config.tokenBytes"/>
              <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.tokens.tokenBytesHint') }}</MutedText>
            </div>
            <div>
              <FieldLabel class="mb-1">{{ t('adminSecurity.tokens.sessionMinutes') }}</FieldLabel>
              <NumberInput v-model="config.sessionMinutes"/>
              <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.tokens.sessionMinutesHint') }}</MutedText>
            </div>
            <div>
              <FieldLabel class="mb-1">{{ t('adminSecurity.tokens.verifyTokenHours') }}</FieldLabel>
              <NumberInput v-model="config.verifyTokenHours"/>
              <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.tokens.verifyTokenHoursHint') }}</MutedText>
            </div>
            <div>
              <FieldLabel class="mb-1">{{ t('adminSecurity.tokens.passwordTokenHours') }}</FieldLabel>
              <NumberInput v-model="config.passwordTokenHours"/>
              <MutedText tag="div" class="mt-1" size="sm">{{ t('adminSecurity.tokens.passwordTokenHoursHint') }}</MutedText>
            </div>
          </div>

          <div class="flex justify-end">
            <SaveButton :action="save"/>
          </div>
        </NeutralContainer>

        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('adminSecurity.tokens.pepper.title') }}</SubHeader>
          <MutedText tag="p" size="sm">{{ t('adminSecurity.tokens.pepper.hint') }}</MutedText>

          <div class="flex items-center justify-between gap-3">
            <div>
              <SuccessBadge v-if="config.tokenPepperConfigured">
                {{ t('adminSecurity.tokens.pepper.configured') }}
              </SuccessBadge>
              <ErrorBadge v-else>
                {{ t('adminSecurity.tokens.pepper.missing') }}
              </ErrorBadge>
            </div>
            <PrimaryButton v-if="!config.tokenPepperConfigured" :disabled="generating" @click="generatePepper">
              {{ generating ? t('common.loading') : t('adminSecurity.tokens.pepper.generate') }}
            </PrimaryButton>
          </div>

          <Alert variant="info">{{ t('adminSecurity.tokens.pepper.restartWarning') }}</Alert>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
