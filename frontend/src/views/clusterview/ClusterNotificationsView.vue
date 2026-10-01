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
import ClusterMailSwitch from './clusternotificationsview/ClusterMailSwitch.vue'

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
</script>

<template>
  <ViewContent :title="t('pages.cluster-notifications.title')" :subtitle="t('pages.cluster-notifications.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>
      <Alert v-if="saved" variant="success">{{ t('clusterNotifications.saved') }}</Alert>
      <ClusterMailSwitch v-if="!loading && settings" :settings="settings" @toggle="toggleMail"/>
    </div>
  </ViewContent>
</template>
