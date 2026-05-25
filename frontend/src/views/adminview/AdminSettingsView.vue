/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import { adminSettings } from '@/api'
import type { AuthConfig } from '@/api/adminSettings'
import MutedText from '@/components/typography/MutedText.vue'
import { THEMES, Feel } from '@/theme/themes'
import { useTheme } from '@/composables/useTheme'

const { t } = useI18n()
const themeCtrl = useTheme()

const themeOptions = Object.entries(THEMES).map(([key, theme]) => ({ value: key, label: theme.label }))

const loading = ref(true)
const error = ref('')
const success = ref('')
const registrationEnabled = ref(true)
const instanceDefaultTheme = ref('ember')
const instanceDefaultFeel = ref('ROUNDED')
const instanceLockFeel = ref(false)

// Auth config
const authConfig = ref<AuthConfig>({
  tokenBytes: 32,
  verifyTokenHours: 24,
  passwordTokenHours: 72,
  sessionMinutes: 30,
})

async function loadSettings() {
  loading.value = true
  error.value = ''
  try {
    const [settings, auth] = await Promise.all([
      adminSettings.getSettings(),
      adminSettings.getAuthConfig(),
    ])
    registrationEnabled.value = settings.stationRegistrationEnabled
    instanceDefaultTheme.value = settings.instanceDefaultTheme ?? 'ember'
    instanceDefaultFeel.value = settings.instanceDefaultFeel ?? 'ROUNDED'
    instanceLockFeel.value = settings.instanceLockFeel ?? false
    authConfig.value = auth
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function toggleRegistration(value: boolean) {
  error.value = ''
  success.value = ''
  try {
    const result = await adminSettings.updateSettings({
      stationRegistrationEnabled: value,
      instanceDefaultTheme: instanceDefaultTheme.value,
      instanceDefaultFeel: instanceDefaultFeel.value,
      instanceLockFeel: instanceLockFeel.value,
    })
    registrationEnabled.value = result.stationRegistrationEnabled
    showSuccess()
  } catch {
    error.value = t('common.error')
    registrationEnabled.value = !value
  }
}

const themeSaving = ref(false)

watch(instanceDefaultTheme, (newTheme) => {
  themeCtrl.applyTheme(newTheme)
})

async function saveInstanceTheme() {
  themeSaving.value = true
  error.value = ''
  success.value = ''
  try {
    const result = await adminSettings.updateSettings({
      stationRegistrationEnabled: registrationEnabled.value,
      instanceDefaultTheme: instanceDefaultTheme.value,
      instanceDefaultFeel: instanceDefaultFeel.value,
      instanceLockFeel: instanceLockFeel.value,
    })
    instanceDefaultTheme.value = result.instanceDefaultTheme ?? 'ember'
    instanceDefaultFeel.value = result.instanceDefaultFeel ?? 'ROUNDED'
    instanceLockFeel.value = result.instanceLockFeel ?? false
    showSuccess()
  } catch {
    error.value = t('common.error')
  } finally {
    themeSaving.value = false
  }
}

async function saveAuthConfig() {
  error.value = ''
  success.value = ''
  try {
    const result = await adminSettings.updateAuthConfig(authConfig.value)
    authConfig.value = result
    showSuccess()
  } catch {
    error.value = t('common.error')
  }
}

function showSuccess() {
  success.value = t('adminSettings.saved')
  setTimeout(() => {
    success.value = ''
  }, 3000)
}

onMounted(async () => {
  await loadSettings()
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading">
        <!-- General Settings -->
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('adminSettings.title') }}</SectionHeader>

          <div class="flex items-center justify-between">
            <div>
              <div class="font-medium">{{ t('adminSettings.stationRegistration') }}</div>
              <div class="text-sm text-(--text-muted)">
                {{ t('adminSettings.stationRegistrationHint') }}
              </div>
            </div>
            <ToggleInput :model-value="registrationEnabled" @update:model-value="toggleRegistration" />
          </div>
        </NeutralContainer>

        <!-- Instance Theme -->
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('adminSettings.theme.title') }}</SectionHeader>
          <p class="text-sm text-(--text-muted)">{{ t('adminSettings.theme.hint') }}</p>

          <div class="space-y-1">
            <FieldLabel>{{ t('adminSettings.theme.defaultTheme') }}</FieldLabel>
            <SelectInput v-model="instanceDefaultTheme">
              <option v-for="opt in themeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </SelectInput>
          </div>

          <div class="space-y-1">
            <FieldLabel>{{ t('adminSettings.theme.defaultFeel') }}</FieldLabel>
            <SelectInput v-model="instanceDefaultFeel">
              <option :value="Feel.ROUNDED">{{ t('theme.feelROUNDED') }}</option>
              <option :value="Feel.CORNERS">{{ t('theme.feelCORNERS') }}</option>
            </SelectInput>
          </div>

          <div class="flex items-center justify-between">
            <div>
              <div class="font-medium">{{ t('adminSettings.theme.lockFeel') }}</div>
              <div class="text-sm text-(--text-muted)">{{ t('adminSettings.theme.lockFeelHint') }}</div>
            </div>
            <ToggleInput v-model="instanceLockFeel" />
          </div>

          <div class="flex justify-end">
            <PrimaryButton :disabled="themeSaving" @click="saveInstanceTheme">
              {{ t('adminSettings.legal.save') }}
            </PrimaryButton>
          </div>
        </NeutralContainer>

        <!-- Auth Settings -->
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('adminSettings.auth.title') }}</SectionHeader>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <FieldLabel class="mb-1">{{ t('adminSettings.auth.tokenBytes') }}</FieldLabel>
              <NumberInput v-model="authConfig.tokenBytes" />
              <MutedText tag="div" class="mt-1">{{ t('adminSettings.auth.tokenBytesHint') }}</MutedText>
            </div>
            <div>
              <FieldLabel class="mb-1">{{ t('adminSettings.auth.verifyTokenHours') }}</FieldLabel>
              <NumberInput v-model="authConfig.verifyTokenHours" />
              <MutedText tag="div" class="mt-1">{{ t('adminSettings.auth.verifyTokenHoursHint') }}</MutedText>
            </div>
            <div>
              <FieldLabel class="mb-1">{{ t('adminSettings.auth.passwordTokenHours') }}</FieldLabel>
              <NumberInput v-model="authConfig.passwordTokenHours" />
              <MutedText tag="div" class="mt-1">{{ t('adminSettings.auth.passwordTokenHoursHint') }}</MutedText>
            </div>
            <div>
              <FieldLabel class="mb-1">{{ t('adminSettings.auth.sessionMinutes') }}</FieldLabel>
              <NumberInput v-model="authConfig.sessionMinutes" />
              <MutedText tag="div" class="mt-1">{{ t('adminSettings.auth.sessionMinutesHint') }}</MutedText>
            </div>
          </div>

          <div class="flex justify-end">
            <PrimaryButton @click="saveAuthConfig">{{ t('adminSettings.legal.save') }}</PrimaryButton>
          </div>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
