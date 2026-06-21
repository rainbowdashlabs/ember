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
import SelectInput from '@/components/input/select/SelectInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import { adminSettings } from '@/api'
import { THEMES, Feel } from '@/theme/themes'
import { useTheme } from '@/composables/useTheme'

const { t } = useI18n()
const themeCtrl = useTheme()

const themeOptions = Object.entries(THEMES).map(([key, theme]) => ({ value: key, label: theme.label }))

const loading = ref(true)
const error = ref('')
const success = ref('')
const registrationEnabled = ref(true)
const forcePrideFlag = ref(false)
const instanceDefaultTheme = ref('ember')
const instanceDefaultFeel = ref('ROUNDED')
const instanceLockFeel = ref(false)

async function loadSettings() {
  loading.value = true
  error.value = ''
  try {
    const settings = await adminSettings.getSettings()
    registrationEnabled.value = settings.stationRegistrationEnabled
    forcePrideFlag.value = settings.forcePrideFlag ?? false
    instanceDefaultTheme.value = settings.instanceDefaultTheme ?? 'ember'
    instanceDefaultFeel.value = settings.instanceDefaultFeel ?? 'ROUNDED'
    instanceLockFeel.value = settings.instanceLockFeel ?? false
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function buildSettings() {
  return {
    stationRegistrationEnabled: registrationEnabled.value,
    instanceDefaultTheme: instanceDefaultTheme.value,
    instanceDefaultFeel: instanceDefaultFeel.value,
    instanceLockFeel: instanceLockFeel.value,
    forcePrideFlag: forcePrideFlag.value,
  }
}

async function toggleRegistration(value: boolean) {
  error.value = ''
  success.value = ''
  try {
    const result = await adminSettings.updateSettings({ ...buildSettings(), stationRegistrationEnabled: value })
    registrationEnabled.value = result.stationRegistrationEnabled
    showSuccess()
  } catch {
    error.value = t('common.error')
    registrationEnabled.value = !value
  }
}

watch(instanceDefaultTheme, (newTheme) => {
  themeCtrl.applyTheme(newTheme)
})

async function saveInstanceTheme() {
  error.value = ''
  try {
    const result = await adminSettings.updateSettings(buildSettings())
    instanceDefaultTheme.value = result.instanceDefaultTheme ?? 'ember'
    instanceDefaultFeel.value = result.instanceDefaultFeel ?? 'ROUNDED'
    instanceLockFeel.value = result.instanceLockFeel ?? false
  } catch (e) {
    error.value = t('common.error')
    throw e
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

          <div class="flex items-center justify-between">
            <div>
              <div class="font-medium">{{ t('adminSettings.forcePrideFlag') }}</div>
              <div class="text-sm text-(--text-muted)">{{ t('adminSettings.forcePrideFlagHint') }}</div>
            </div>
            <ToggleInput v-model="forcePrideFlag" @update:model-value="saveInstanceTheme" />
          </div>
        </NeutralContainer>

        <!-- Instance Theme -->
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('adminSettings.theme.title') }}</SectionHeader>
          <p class="text-sm text-(--text-muted)">{{ t('adminSettings.theme.hint') }}</p>

          <div class="space-y-1">
            <FieldLabel>{{ t('adminSettings.theme.defaultTheme') }}</FieldLabel>
            <SelectInput v-model="instanceDefaultTheme" class="w-full">
              <option v-for="opt in themeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </SelectInput>
          </div>

          <div class="space-y-1">
            <FieldLabel>{{ t('adminSettings.theme.defaultFeel') }}</FieldLabel>
            <SelectInput v-model="instanceDefaultFeel" class="w-full">
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
            <SaveButton :action="saveInstanceTheme"/>
          </div>
        </NeutralContainer>

      </template>
    </div>
  </ViewContent>
</template>
