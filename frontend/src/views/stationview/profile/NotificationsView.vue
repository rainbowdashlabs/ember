/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {userSettings} from '@/api'
import type {UserSettings} from '@/api/types'
import NotificationsSection from './settingsview/NotificationsSection.vue'

const {t} = useI18n()

const settings = ref<UserSettings | null>(null)
const loading = ref(true)
const error = ref('')
const saved = ref(false)

async function loadData() {
  loading.value = true
  try { settings.value = await userSettings.getSettings() }
  catch { error.value = t('common.error') }
  finally { loading.value = false }
}

async function save() {
  if (!settings.value) return
  saved.value = false; error.value = ''
  try {
    settings.value = await userSettings.updateSettings({
      emailEnabled: settings.value.emailEnabled,
      notifications: settings.value.notifications,
    })
    saved.value = true
  } catch { error.value = t('common.error') }
}

function toggleEmailEnabled() {
  if (!settings.value) return
  settings.value.emailEnabled = !settings.value.emailEnabled
  save()
}

function toggleApp(type: string) {
  if (!settings.value) return
  const current = settings.value.notifications?.[type] ?? {app: true, email: false}
  settings.value.notifications[type] = {app: !current.app, email: current.email}
  save()
}

function toggleEmail(type: string) {
  if (!settings.value) return
  const current = settings.value.notifications?.[type] ?? {app: true, email: false}
  settings.value.notifications[type] = {app: current.app, email: !current.email}
  save()
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="saved" variant="success">{{ t('userSettings.saved') }}</Alert>

      <template v-if="!loading && settings">
        <NotificationsSection
          :settings="settings"
          @toggle-email-enabled="toggleEmailEnabled"
          @toggle-app="toggleApp"
          @toggle-email="toggleEmail"
        />
      </template>
    </div>
  </ViewContent>
</template>
