/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import InstanceMailPanel from './adminmailingview/InstanceMailPanel.vue'
import ClearProvidersModal from '@/components/mail/ClearProvidersModal.vue'
import MailWebhookPanel from '@/components/mail/MailWebhookPanel.vue'
import MailProviderFreeTiers from '@/components/mail/MailProviderFreeTiers.vue'
import MailProviderChain from '@/components/mail/MailProviderChain.vue'
import {
  getInstanceProviders,
  testInstanceProvider,
  updateInstanceProviders,
  type MailProvider,
} from '@/api/mailProviders'
import {useSession} from '@/composables/useSession'
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
  initial: {notificationDigestIntervalMinutes: 60},
  fetch: () => adminSettings.getMailingConfig(),
  formatError: describeAxiosError,
})

const showClearModal = ref(false)
const testMailSent = ref(false)
const testResult = ref('')

const {sessionInfo} = useSession()
const ownAddress = computed(() => sessionInfo.value?.account?.email ?? '')

const providers = ref<MailProvider[]>([])
/** Whether the list on screen is the stored one. Nothing may be saved before it is. */
const providersLoaded = ref(false)

/**
 * Tries the stored provider rather than what is on screen, so the result says something about what
 * would actually carry the post. Anything unsaved has to be saved first to be tried.
 */
async function test(position: number, recipient: string) {
  testResult.value = ''
  try {
    const result = await testInstanceProvider(position, recipient)
    testResult.value = result.success
        ? t('mailChain.testOk', {position: position + 1, recipient})
        : t('mailChain.testFailed', {position: position + 1, error: result.error ?? ''})
  } catch {
    testResult.value = t('common.error')
  }
}

onMounted(async () => {
  try {
    const chain = await getInstanceProviders()
    providers.value = chain.fallbacks ?? []
    providersLoaded.value = true
  } catch {
    testResult.value = t('mailChain.loadFailed')
  }
})

async function saveProviders() {
  if (!providersLoaded.value) return
  const chain = await updateInstanceProviders({attempts: 2, fallbacks: providers.value})
  providers.value = chain.fallbacks ?? []
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
  providers.value = []
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
        <Alert v-if="testResult" variant="info">{{ testResult }}</Alert>

        <MailProviderChain
            v-model:providers="providers"
            :save="saveProviders"
            :default-recipient="ownAddress"
            :ready="providersLoaded"
            @test="test"
            @clear="showClearModal = true"
        >
          <template #webhook="{provider, position}">
            <MailWebhookPanel
                :url="providers[position]?.deliveryWebhookUrl"
                :provider="provider"
                :regenerate="adminSettings.regenerateWebhookKey"
            />
          </template>
        </MailProviderChain>

        <InstanceMailPanel
            v-model="mailingConfig"
            :clearing="clearing"
            :sending-test-mail="sendingTestMail"
            :test-mail-sent="testMailSent"
            :has-provider="providers.length > 0"
            :save="saveMailingConfig"
            @clear="showClearModal = true"
            @test-mail="sendTestMail"
        />

        <MailProviderFreeTiers/>
      </template>

      <ClearProvidersModal v-model="showClearModal" :clearing="clearing" @confirm="clearMailingConfig"/>
    </div>
  </ViewContent>
</template>
