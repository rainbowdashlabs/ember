/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import GeneralFieldsPanel from './adminmailingview/GeneralFieldsPanel.vue'
import SmtpPanel from './adminmailingview/SmtpPanel.vue'
import {adminSettings} from '@/api'
import type {MailingConfig} from '@/api/adminSettings'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {t} = useI18n()

function describeAxiosError(e: unknown): string {
  const data = (e as {response?: {data?: {title?: string; message?: string}}})?.response?.data
  const raw = data?.title ?? data?.message
  return raw ? t('adminSettings.mailing.saveFailed', {error: raw}) : t('common.error')
}

const {config: mailingConfig, loading, error, runWith, reload} = useConfigPanel<MailingConfig>({
  initial: {
    provider: 'SMTP',
    senderAddress: '',
    senderName: '',
    user: '',
    password: '',
    apiKey: '',
    smtpHost: '',
    smtpPort: 665,
    smtpSsl: false,
    dailySendLimit: 200,
    notificationDigestIntervalMinutes: 60,
  },
  fetch: () => adminSettings.getMailingConfig(),
  formatError: describeAxiosError,
})

const showClearModal = ref(false)
const clearing = ref(false)
const sendingTestMail = ref(false)
const testMailSent = ref(false)

async function saveMailingConfig() {
  await runWith(() => adminSettings.updateMailingConfig(mailingConfig.value), {rethrow: true})
}

async function sendTestMail() {
  sendingTestMail.value = true
  testMailSent.value = false
  try {
    await adminSettings.sendTestMail()
    testMailSent.value = true
  } catch (e) {
    error.value = describeAxiosError(e)
  } finally {
    sendingTestMail.value = false
  }
}

async function clearMailingConfig() {
  clearing.value = true
  try {
    await adminSettings.clearMailingConfig()
    showClearModal.value = false
    await reload()
  } catch (e) {
    error.value = describeAxiosError(e)
  } finally {
    clearing.value = false
  }
}
</script>

<template>
  <ViewContent :title="t('pages.admin-mailing.title')" :subtitle="t('pages.admin-mailing.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <NeutralContainer class="space-y-4">
          <GeneralFieldsPanel v-model="mailingConfig"/>
          <SmtpPanel v-if="mailingConfig.provider === 'SMTP'" v-model="mailingConfig"/>
          <div class="flex justify-end gap-2 flex-wrap">
            <ErrorButton :icon="['fas', 'trash']" :disabled="clearing" @click="showClearModal = true">
              {{ t('adminSettings.mailing.clear') }}
            </ErrorButton>
            <SecondaryButton
                v-if="mailingConfig.provider !== 'NONE'"
                :icon="['fas', 'paper-plane']"
                :disabled="sendingTestMail"
                @click="sendTestMail">
              {{ sendingTestMail ? t('common.loading') : t('adminSettings.mailing.testMail') }}
            </SecondaryButton>
            <SaveButton :action="saveMailingConfig"/>
          </div>
          <Alert v-if="testMailSent" variant="success">{{ t('adminSettings.mailing.testMailSent') }}</Alert>
        </NeutralContainer>
      </template>

      <Modal v-model="showClearModal">
        <div class="space-y-4">
          <p>{{ t('adminSettings.mailing.clearConfirm') }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton :disabled="clearing" @click="showClearModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton :icon="['fas', 'trash']" :disabled="clearing" @click="clearMailingConfig">
              {{ t('adminSettings.mailing.clear') }}
            </ErrorButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
