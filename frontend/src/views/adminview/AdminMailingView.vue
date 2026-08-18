/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import MailingConfigPanel from './adminmailingview/MailingConfigPanel.vue'
import MailWebhookPanel from '@/components/mail/MailWebhookPanel.vue'
import MailProviderFreeTiers from '@/components/mail/MailProviderFreeTiers.vue'
import MailFallbackChain from '@/components/mail/MailFallbackChain.vue'
import {getInstanceFallbacks, updateInstanceFallbacks, type MailFallback} from '@/api/mailFallbacks'
import {adminSettings} from '@/api'
import type {MailingConfig} from '@/api/adminSettings'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useAsyncAction} from '@/composables/useAsyncAction'

const {t} = useI18n()

function describeAxiosError(e: unknown): string {
  const data = (e as {response?: {data?: {title?: string; message?: string}}})?.response?.data
  const raw = data?.title ?? data?.message
  return raw ? t('adminSettings.mailing.saveFailed', {error: raw}) : t('common.error')
}

const {config: mailingConfig, loading, error: panelError, runWith, reload} = useConfigPanel<MailingConfig>({
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
const testMailSent = ref(false)

const fallbacks = ref<MailFallback[]>([])
const primaryAttempts = ref(2)

onMounted(async () => {
  const chain = await getInstanceFallbacks()
  fallbacks.value = chain.fallbacks ?? []
  primaryAttempts.value = chain.attempts ?? 2
})

async function saveFallbacks() {
  const chain = await updateInstanceFallbacks({attempts: primaryAttempts.value, fallbacks: fallbacks.value})
  fallbacks.value = chain.fallbacks ?? []
  primaryAttempts.value = chain.attempts ?? 2
}

async function saveMailingConfig() {
  await runWith(() => adminSettings.updateMailingConfig(mailingConfig.value), {rethrow: true})
}

const {running: sendingTestMail, error: testMailError, run: sendTestMail} = useAsyncAction(async () => {
  testMailSent.value = false
  await adminSettings.sendTestMail()
  testMailSent.value = true
}, {formatError: describeAxiosError})

const {running: clearing, error: clearError, run: clearMailingConfig} = useAsyncAction(async () => {
  await adminSettings.clearMailingConfig()
  showClearModal.value = false
  await reload()
}, {formatError: describeAxiosError})

const error = computed(() => panelError.value || testMailError.value || clearError.value)
</script>

<template>
  <ViewContent :title="t('pages.admin-mailing.title')" :subtitle="t('pages.admin-mailing.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <MailingConfigPanel
            v-model="mailingConfig"
            :clearing="clearing"
            :sending-test-mail="sendingTestMail"
            :test-mail-sent="testMailSent"
            :save="saveMailingConfig"
            @clear="showClearModal = true"
            @test-mail="sendTestMail"
        />

        <MailFallbackChain
            v-model:fallbacks="fallbacks"
            v-model:primaryAttempts="primaryAttempts"
            :save="saveFallbacks"
            show-primary-attempts
        />

        <MailWebhookPanel
            :url="mailingConfig.deliveryWebhookUrl"
            :provider="mailingConfig.provider"
            :regenerate="adminSettings.regenerateWebhookKey"
        />

        <MailProviderFreeTiers/>
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
