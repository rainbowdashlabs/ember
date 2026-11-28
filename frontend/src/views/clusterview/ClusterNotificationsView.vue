/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Alert from '@/components/feedback/Alert.vue'
import {clusters} from '@/api'
import type {ClusterMail} from '@/api/generated/schema'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {clusterInbox} from '@/api/notifications'
import NotificationInbox from '@/components/notifications/NotificationInbox.vue'
import ClusterMailSwitch from './clusternotificationsview/ClusterMailSwitch.vue'
import {usePushAvailability} from '@/composables/usePushAvailability'

const {t} = useI18n()

const {config: settings, loading, failure, runWith} = useConfigPanel<ClusterMail | null>({
  initial: null,
  fetch: () => clusters.getMailSettings(),
})

const saved = ref(false)

/** Stores the switch at once, the way the station's own mail switch does. */
async function toggleMail() {
  if (!settings.value) return
  saved.value = false
  const wanted = !settings.value.emailEnabled
  await runWith(async () => {
    const updated = await clusters.updateMailSettings(wanted)
    saved.value = true
    return updated
  })
}

/** Whether this instance pushes; without it the push switch stays hidden. */
const {available: pushAvailable} = usePushAvailability()

async function togglePush() {
  if (!settings.value) return
  saved.value = false
  const wanted = !settings.value.pushEnabled
  await runWith(async () => {
    const updated = await clusters.updatePushSetting(wanted)
    saved.value = true
    return updated
  })
}
</script>

<template>
  <ViewContent :title="t('pages.cluster-notifications.title')" :subtitle="t('pages.cluster-notifications.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>
      <Alert v-if="saved" variant="success">{{ t('clusterNotifications.saved') }}</Alert>
      <NotificationInbox :api="clusterInbox"/>
      <ClusterMailSwitch v-if="!loading && settings" :settings="settings" :push-available="pushAvailable"
                         @toggle="toggleMail" @toggle-push="togglePush"/>
    </div>
  </ViewContent>
</template>
