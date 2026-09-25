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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
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
import {apiErrorMessage} from '@/util/apiError'
import {describeFailure, type Failure} from '@/util/failure'

const {t} = useI18n()

/**
 * The mail provider's own words where it wrote any, and the described failure otherwise.
 *
 * <p>A mail chain refuses for reasons only the provider knows: a rejected key, a sender address it
 * will not accept, a quota. Losing that sentence leaves the one person who could fix it guessing.
 */
function mailingFailed(e: unknown): string {
  const said = apiErrorMessage(e)
  return said ? t('adminSettings.mailing.saveFailed', {error: said}) : describeFailure(e, t).message
}

const {config: mailingConfig, loading, failure: configFailure, runWith, reload} = useConfigPanel<MailingConfig>({
  initial: {notificationDigestIntervalMinutes: 60},
  fetch: () => adminSettings.getMailingConfig(),
  formatError: mailingFailed,
})

const showClearModal = ref(false)
const testMailSent = ref(false)
/** Only the list failing to load, which is a page-level problem rather than one entry's. */
const chainFailure = ref<Failure | null>(null)

const {sessionInfo} = useSession()
const ownAddress = computed(() => sessionInfo.value?.account?.email ?? '')

const providers = ref<MailProvider[]>([])
/** Whether the list on screen is the stored one. Nothing may be saved before it is. */
const providersLoaded = ref(false)

/**
 * Tries the stored provider rather than what is on screen, so the result says something about what
 * would actually carry the post. Anything unsaved has to be saved first to be tried.
 */
const testingPosition = ref<number | null>(null)
const testResults = ref<Record<number, {ok: boolean; message: string}>>({})

async function test(position: number, recipient: string) {
  testingPosition.value = position
  try {
    const result = await testInstanceProvider(position, recipient)
    testResults.value = {
      ...testResults.value,
      [position]: result.success
          ? {ok: true, message: t('mailChain.testOk', {position: position + 1, recipient})}
          : {ok: false, message: t('mailChain.testFailed', {position: position + 1, error: result.error ?? ''})},
    }
  } catch (e) {
    const reason = apiErrorMessage(e) ?? describeFailure(e, t).message
    testResults.value = {
      ...testResults.value,
      [position]: {ok: false, message: t('mailChain.testFailed', {position: position + 1, error: reason})},
    }
  } finally {
    testingPosition.value = null
  }
}

onMounted(async () => {
  try {
    const chain = await getInstanceProviders()
    providers.value = chain.fallbacks ?? []
    providersLoaded.value = true
  } catch (e) {
    chainFailure.value = {...describeFailure(e, t), message: t('mailChain.loadFailed')}
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

const {running: sendingTestMail, failure: testMailFailure, run: sendTestMail} = useAsyncAction(async () => {
  testMailSent.value = false
  await adminSettings.sendTestMail()
  testMailSent.value = true
}, {formatError: mailingFailed})

const {running: clearing, failure: clearFailure, run: clearMailingConfig} = useAsyncAction(async () => {
  await adminSettings.clearMailingConfig()
  showClearModal.value = false
  providers.value = []
  await reload()
}, {formatError: mailingFailed})
</script>

<template>
  <ViewContent :title="t('pages.admin-mailing.title')" :subtitle="t('pages.admin-mailing.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="configFailure"/>
      <FailureAlert :failure="testMailFailure"/>
      <FailureAlert :failure="clearFailure"/>

      <template v-if="!loading">
        <FailureAlert :failure="chainFailure"/>

        <MailProviderChain
            v-model:providers="providers"
            :save="saveProviders"
            :default-recipient="ownAddress"
            :ready="providersLoaded"
            :testing-position="testingPosition"
            :test-results="testResults"
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
