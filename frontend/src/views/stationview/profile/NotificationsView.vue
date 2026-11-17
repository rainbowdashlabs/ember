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
import type {PushKey, SettingsResponse} from '@/api/generated/schema'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {onMounted, ref} from 'vue'
import NotificationsSection from './settingsview/NotificationsSection.vue'
import FeedSection from './settingsview/FeedSection.vue'
import PushDeviceCard from './settingsview/PushDeviceCard.vue'
import {getPushKey} from '@/api/push'

const {t} = useI18n()

const {config: settings, loading, failure, runWith} = useConfigPanel<SettingsResponse | null>({
  initial: null,
  fetch: () => userSettings.getSettings(),
})

const saved = ref(false)

/** The installation's push key, and whether it pushes at all; without it the push card and column stay hidden. */
const push = ref<PushKey | null>(null)

onMounted(async () => {
  try {
    push.value = await getPushKey()
  } catch {
    push.value = null
  }
})

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
  const current = settings.value.notifications?.[type] ?? {app: true, email: false, feed: true, push: true}
  settings.value.notifications[type] = {app: !current.app, email: current.email, feed: current.feed, push: current.push}
  save()
}

function toggleEmail(type: string) {
  if (!settings.value) return
  const current = settings.value.notifications?.[type] ?? {app: true, email: false, feed: true, push: true}
  settings.value.notifications[type] = {app: current.app, email: !current.email, feed: current.feed, push: current.push}
  save()
}

function togglePush(type: string) {
  if (!settings.value) return
  const current = settings.value.notifications?.[type] ?? {app: true, email: false, feed: true, push: true}
  settings.value.notifications[type] = {app: current.app, email: current.email, feed: current.feed, push: !(current.push ?? true)}
  save()
}

function toggleFeed(type: string) {
  if (!settings.value) return
  const current = settings.value.notifications?.[type] ?? {app: true, email: false, feed: true, push: true}
  settings.value.notifications[type] = {app: current.app, email: current.email, feed: !current.feed, push: current.push}
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
        <PushDeviceCard v-if="push?.enabled" :public-key="push.publicKey"/>

        <NotificationsSection
          :settings="settings"
          :push-available="push?.enabled === true"
          @toggle-email-enabled="toggleEmailEnabled"
          @toggle-app="toggleApp"
          @toggle-email="toggleEmail"
          @toggle-feed="toggleFeed"
          @toggle-push="togglePush"
        />

        <FeedSection />
      </template>
    </div>
  </ViewContent>
</template>
