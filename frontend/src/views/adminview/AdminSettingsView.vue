/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import GeneralPanel from '@/views/adminview/adminsettingsview/GeneralPanel.vue'
import ThemePanel from '@/views/adminview/adminsettingsview/ThemePanel.vue'
import {adminSettings} from '@/api'
import {useTheme} from '@/composables/useTheme'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()
const themeCtrl = useTheme()

const success = ref('')
const registrationEnabled = ref(true)
const forcePrideFlag = ref(false)
const instanceDefaultTheme = ref('ember')
const instanceDefaultFeel = ref('ROUNDED')
const instanceLockFeel = ref(false)

const {loading, error} = useAsyncLoader(async () => {
  const settings = await adminSettings.getSettings()
  registrationEnabled.value = settings.stationRegistrationEnabled
  forcePrideFlag.value = settings.forcePrideFlag ?? false
  instanceDefaultTheme.value = settings.instanceDefaultTheme ?? 'ember'
  instanceDefaultFeel.value = settings.instanceDefaultFeel ?? 'ROUNDED'
  instanceLockFeel.value = settings.instanceLockFeel ?? false
})

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
    const result = await adminSettings.updateSettings({...buildSettings(), stationRegistrationEnabled: value})
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
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading">
        <GeneralPanel
            v-model:forcePrideFlag="forcePrideFlag"
            :registration-enabled="registrationEnabled"
            @save-pride="saveInstanceTheme"
            @toggle-registration="toggleRegistration"
        />
        <ThemePanel
            v-model:defaultFeel="instanceDefaultFeel"
            v-model:defaultTheme="instanceDefaultTheme"
            v-model:lockFeel="instanceLockFeel"
            :save="saveInstanceTheme"
        />
      </template>
    </div>
  </ViewContent>
</template>
