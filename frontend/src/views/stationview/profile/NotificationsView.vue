/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Alert from '@/components/feedback/Alert.vue'
import {userSettings} from '@/api'
import type {SettingsResponse} from '@/api/generated/schema'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {ref} from 'vue'
import NotificationsSection from './settingsview/NotificationsSection.vue'
import FeedSection from './settingsview/FeedSection.vue'
import PushDeviceCard from './settingsview/PushDeviceCard.vue'
import {usePushAvailability} from '@/composables/usePushAvailability'

const {t} = useI18n()

const {config: settings, loading, failure, runWith} = useConfigPanel<SettingsResponse | null>({
  initial: null,
  fetch: () => userSettings.getSettings(),
})

const saved = ref(false)

/** The installation's push key, and whether it pushes at all; without it the push card and column stay hidden. */
const {key: push, available: pushAvailable} = usePushAvailability()

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

/** A delivery channel of one notification type, as the settings table switches it. */
type NotificationChannel = 'app' | 'email' | 'feed' | 'push'

/** Switches one channel of one notification type; a type never set yet starts with every channel on. */
function toggle(type: string, channel: NotificationChannel) {
  if (!settings.value) return
  const current = settings.value.notifications?.[type] ?? {app: true, email: true, feed: true, push: true}
  settings.value.notifications[type] = {...current, [channel]: !(current[channel] ?? true)}
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
      <FailureAlert :failure="failure"/>
      <Alert v-if="saved" variant="success">{{ t('userSettings.saved') }}</Alert>

      <template v-if="!loading && settings">
        <PushDeviceCard v-if="push && pushAvailable" :public-key="push.publicKey"/>

        <NotificationsSection
          :settings="settings"
          :push-available="pushAvailable"
          @toggle-email-enabled="toggleEmailEnabled"
          @toggle-app="type => toggle(type, 'app')"
          @toggle-email="type => toggle(type, 'email')"
          @toggle-feed="type => toggle(type, 'feed')"
          @toggle-push="type => toggle(type, 'push')"
        />

        <FeedSection />
      </template>
    </div>
  </ViewContent>
</template>
