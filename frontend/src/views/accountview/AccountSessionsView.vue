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
import SubHeader from '@/components/typography/SubHeader.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {describeFailure} from '@/util/failure'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Modal from '@/components/feedback/Modal.vue'
import {session as sessionApi} from '@/api'
import type {ActiveSession} from '@/api/session'
import SessionsSection from '@/views/stationview/profile/settingsview/SessionsSection.vue'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()
const router = useRouter()

const sessions = ref<ActiveSession[]>([])
const showInvalidateAllModal = ref(false)

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

async function invalidateAll() {
  failure.value = null
  try {
    await sessionApi.invalidateAllSessions()
    showInvalidateAllModal.value = false
    localStorage.removeItem('session_token')
    localStorage.removeItem('session_expires_at')
    router.push({name: 'login'})
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}
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
          @invalidate-all="showInvalidateAllModal = true"
        />
      </template>

      <Modal v-model="showInvalidateAllModal">
        <div class="space-y-4 p-4">
          <SubHeader>{{ t('userSettings.invalidateAllTitle') }}</SubHeader>
          <ErrorContainer>
            <p class="text-sm">{{ t('userSettings.invalidateAllWarning') }}</p>
          </ErrorContainer>
          <ButtonRow align="end">
            <SecondaryButton @click="showInvalidateAllModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton @click="invalidateAll">{{ t('userSettings.invalidateAll') }}</ErrorButton>
          </ButtonRow>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
