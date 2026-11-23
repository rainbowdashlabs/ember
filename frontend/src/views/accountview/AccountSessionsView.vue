/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {describeFailure} from '@/util/failure'
import Spinner from '@/components/feedback/Spinner.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {session as sessionApi} from '@/api'
import type {ActiveSession} from '@/api/generated/schema'
import SessionsSection from '@/views/stationview/profile/settingsview/SessionsSection.vue'
import {removePushDevice} from '@/api/push'
import {unsubscribeBrowser} from '@/util/pushSupport'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useConfirmAction} from '@/composables/useConfirmAction'

const {t} = useI18n()
const router = useRouter()

const sessions = ref<ActiveSession[]>([])

const {loading, failure} = useAsyncLoader(async () => {
  sessions.value = await sessionApi.getActiveSessions()
})

async function invalidateSession(id: number) {
  failure.value = null
  try {
    await sessionApi.invalidateSession(id)
    sessions.value = sessions.value.filter(s => s.id !== id)
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

/** Stops pushes to a sign-in's device and keeps the sign-in; on this device the browser lets go of it too. */
async function removePush(session: ActiveSession) {
  if (session.pushDeviceId == null) return
  failure.value = null
  try {
    if (session.isCurrent) await unsubscribeBrowser()
    await removePushDevice(session.pushDeviceId)
    sessions.value = sessions.value.map(s => s.id === session.id ? {...s, pushDeviceId: null} : s)
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

const {show: showInvalidateAllModal, request: requestInvalidateAll, confirm: invalidateAll} = useConfirmAction<true>({
  onConfirm: async () => {
    failure.value = null
    await sessionApi.invalidateAllSessions()
  },
  onSuccess: () => {
    void router.push({name: 'login'})
  },
  failure,
})
</script>

<template>
  <ViewContent :title="t('pages.account-sessions.title')" :subtitle="t('pages.account-sessions.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>

      <template v-if="!loading">
        <SessionsSection
          :sessions="sessions"
          @invalidate="invalidateSession"
          @remove-push="removePush"
          @invalidate-all="requestInvalidateAll(true)"
        />
      </template>

      <ConfirmDeleteModal
          v-model="showInvalidateAllModal"
          :title="t('userSettings.invalidateAllTitle')"
          :message="t('userSettings.invalidateAllWarning')"
          :confirm-label="t('userSettings.invalidateAll')"
          @confirm="invalidateAll"
      />
    </div>
  </ViewContent>
</template>
