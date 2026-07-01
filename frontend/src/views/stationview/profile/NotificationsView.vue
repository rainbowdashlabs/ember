/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {userSettings} from '@/api'
import type {UserSettings} from '@/api/types'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {ref} from 'vue'
import NotificationsSection from './settingsview/NotificationsSection.vue'
import FeedSection from './settingsview/FeedSection.vue'

const {t} = useI18n()

const {config: settings, loading, error, runWith} = useConfigPanel<UserSettings | null>({
  initial: null,
  fetch: () => userSettings.getSettings(),
})

const saved = ref(false)

async function save() {
  if (!settings.value) return
  saved.value = false
  const current = settings.value
  await runWith(async () => {
    const updated = await userSettings.updateSettings({
      emailEnabled: current.emailEnabled,
      notifications: current.notifications,
    })
    saved.value = true
    return updated
  })
}

function toggleEmailEnabled() {
  if (!settings.value) return
  settings.value.emailEnabled = !settings.value.emailEnabled
  save()
}

function toggleApp(type: string) {
  if (!settings.value) return
  const current = settings.value.notifications?.[type] ?? {app: true, email: false, feed: true}
  settings.value.notifications[type] = {app: !current.app, email: current.email, feed: current.feed}
  save()
}

function toggleEmail(type: string) {
  if (!settings.value) return
  const current = settings.value.notifications?.[type] ?? {app: true, email: false, feed: true}
  settings.value.notifications[type] = {app: current.app, email: !current.email, feed: current.feed}
  save()
}

function toggleFeed(type: string) {
  if (!settings.value) return
  const current = settings.value.notifications?.[type] ?? {app: true, email: false, feed: true}
  settings.value.notifications[type] = {app: current.app, email: current.email, feed: !current.feed}
  save()
}
</script>

<template>
  <ViewContent
      :title="t('pages.profile-notifications.title')"
      :subtitle="t('pages.profile-notifications.subtitle')"
  >
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
          @toggle-feed="toggleFeed"
        />

        <FeedSection />
      </template>
    </div>
  </ViewContent>
</template>
