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
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import {session as sessionApi} from '@/api'
import type {ActiveSession} from '@/api/types'
import SessionsSection from '@/views/stationview/profile/settingsview/SessionsSection.vue'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()
const router = useRouter()

const sessions = ref<ActiveSession[]>([])
const showInvalidateAllModal = ref(false)

const {loading, error} = useAsyncLoader(async () => {
  sessions.value = await sessionApi.getActiveSessions()
})

async function invalidateSession(id: number) {
  error.value = ''
  try {
    await sessionApi.invalidateSession(id)
    sessions.value = sessions.value.filter(s => s.id !== id)
  } catch {
    error.value = t('common.error')
  }
}

async function invalidateAll() {
  error.value = ''
  try {
    await sessionApi.invalidateAllSessions()
    showInvalidateAllModal.value = false
    localStorage.removeItem('session_token')
    localStorage.removeItem('session_expires_at')
    router.push({name: 'login'})
  } catch {
    error.value = t('common.error')
  }
}
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

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
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showInvalidateAllModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton @click="invalidateAll">{{ t('userSettings.invalidateAll') }}</ErrorButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
