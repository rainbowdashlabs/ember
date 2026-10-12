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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {describeFailure} from '@/util/failure'
import SaveStatus from '@/components/feedback/SaveStatus.vue'
import GeneralPanel from '@/views/adminview/adminsettingsview/GeneralPanel.vue'
import ThemePanel from '@/views/adminview/adminsettingsview/ThemePanel.vue'
import LoggingPanel from '@/views/adminview/adminsettingsview/LoggingPanel.vue'
import {adminSettings} from '@/api'
import {useTheme} from '@/composables/useTheme'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useFlashMessage} from '@/composables/useFlashMessage'

const {t} = useI18n()
const themeCtrl = useTheme()

const {message: savedMessage, flash} = useFlashMessage(2000)
const saving = ref(false)
const registrationEnabled = ref(true)
const forcePrideFlag = ref(false)
const instanceDefaultTheme = ref('ember')
const instanceDefaultFeel = ref('ROUNDED')
const instanceLockFeel = ref(false)
const defaultMailLocale = ref('en')
const availableMailLocales = ref<string[]>([])

const {loading, failure} = useAsyncLoader(async () => {
  const settings = await adminSettings.getSettings()
  registrationEnabled.value = settings.stationRegistrationEnabled
  forcePrideFlag.value = settings.forcePrideFlag ?? false
  instanceDefaultTheme.value = settings.instanceDefaultTheme ?? 'ember'
  instanceDefaultFeel.value = settings.instanceDefaultFeel ?? 'ROUNDED'
  instanceLockFeel.value = settings.instanceLockFeel ?? false
  defaultMailLocale.value = settings.defaultMailLocale ?? 'en'
  availableMailLocales.value = settings.availableMailLocales ?? []
})

function buildSettings() {
  return {
    stationRegistrationEnabled: registrationEnabled.value,
    instanceDefaultTheme: instanceDefaultTheme.value,
    instanceDefaultFeel: instanceDefaultFeel.value,
    instanceLockFeel: instanceLockFeel.value,
    forcePrideFlag: forcePrideFlag.value,
    defaultMailLocale: defaultMailLocale.value,
  }
}

/** Records what went wrong into the one alert this page carries. */
function record(e: unknown) {
  failure.value = describeFailure(e, t)
}

/**
 * Keeps a setting that is saved as soon as it changes, showing its progress beside the page's alerts.
 * A failure is recorded and undone, since there is no button to try again from.
 *
 * @param write what to save
 * @param undo  puts the control back where the server still has it
 */
async function saveInstantly(write: () => Promise<void>, undo: () => void = () => {}) {
  failure.value = null
  saving.value = true
  try {
    await write()
    flash(t('common.saved'))
  } catch (e) {
    undo()
    record(e)
  } finally {
    saving.value = false
  }
}

function saveMailLocale() {
  return saveInstantly(async () => {
    const result = await adminSettings.updateSettings(buildSettings())
    defaultMailLocale.value = result.defaultMailLocale ?? 'en'
  })
}

function savePride() {
  return saveInstantly(async () => {
    const result = await adminSettings.updateSettings(buildSettings())
    forcePrideFlag.value = result.forcePrideFlag ?? false
  })
}

function toggleRegistration(value: boolean) {
  return saveInstantly(async () => {
    const result = await adminSettings.updateSettings({...buildSettings(), stationRegistrationEnabled: value})
    registrationEnabled.value = result.stationRegistrationEnabled
  }, () => { registrationEnabled.value = !value })
}

watch(instanceDefaultTheme, (newTheme) => {
  themeCtrl.applyTheme(newTheme)
})

async function saveInstanceTheme() {
  failure.value = null
  try {
    const result = await adminSettings.updateSettings(buildSettings())
    instanceDefaultTheme.value = result.instanceDefaultTheme ?? 'ember'
    instanceDefaultFeel.value = result.instanceDefaultFeel ?? 'ROUNDED'
    instanceLockFeel.value = result.instanceLockFeel ?? false
  } catch (e) {
    record(e)
    throw e
  }
}

</script>

<template>
  <ViewContent :title="t('pages.admin-settings.title')" :subtitle="t('pages.admin-settings.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>
      <div class="flex justify-end">
        <SaveStatus :saving="saving" :saved="savedMessage !== ''"/>
      </div>

      <template v-if="!loading">
        <GeneralPanel
            v-model:force-pride-flag="forcePrideFlag"
            v-model:default-mail-locale="defaultMailLocale"
            :registration-enabled="registrationEnabled"
            :available-mail-locales="availableMailLocales"
            @save-pride="savePride"
            @save-mail-locale="saveMailLocale"
            @toggle-registration="toggleRegistration"
        />
        <ThemePanel
            v-model:default-feel="instanceDefaultFeel"
            v-model:default-theme="instanceDefaultTheme"
            v-model:lock-feel="instanceLockFeel"
            :save="saveInstanceTheme"
        />
        <LoggingPanel/>
      </template>
    </div>
  </ViewContent>
</template>
