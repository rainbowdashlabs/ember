/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Alert from '@/components/feedback/Alert.vue'
import MailProviderChain from '@/components/mail/MailProviderChain.vue'
import MailDashboard from '@/components/mail/MailDashboard.vue'
import ClearProvidersModal from '@/components/mail/ClearProvidersModal.vue'
import MailWebhookPanel from '@/components/mail/MailWebhookPanel.vue'
import MailProviderFreeTiers from '@/components/mail/MailProviderFreeTiers.vue'
import {
  clearStationProviders,
  getStationMailDashboard,
  getStationProviders,
  getStationWebhook,
  liftStationBlock,
  requeueStationStuckMails,
  regenerateStationWebhookKey,
  saveStationSigningSecret,
  testStationProvider,
  updateStationProviders,
  type MailProvider,
} from '@/api/mailProviders'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {hasPermission, loaded, sessionInfo} = useSession()
const ownAddress = computed(() => sessionInfo.value?.account?.email ?? '')
const router = useRouter()
watch(loaded, (isLoaded) => {
  if (isLoaded && !hasPermission(StationPermission.STATION_MAIL)) {
    router.replace('/station/dashboard/overview')
  }
}, {immediate: true})

const error = ref('')
const success = ref('')

function handleError(msg: string) { error.value = msg; success.value = '' }
function handleSuccess(msg: string) { success.value = msg; error.value = '' }

const providers = ref<MailProvider[]>([])
const signingSecretSet = ref(false)
/** Whether the list on screen is the stored one. Nothing may be saved before it is. */
const providersLoaded = ref(false)

onMounted(async () => {
  try {
    const [entries, webhook] = await Promise.all([getStationProviders(), getStationWebhook()])
    providers.value = entries
    signingSecretSet.value = webhook.signingSecretSet
    providersLoaded.value = true
  } catch {
    handleError(t('mailChain.loadFailed'))
  }
})

async function saveSigningSecret(secret: string) {
  const webhook = await saveStationSigningSecret(secret)
  signingSecretSet.value = webhook.signingSecretSet
  handleSuccess(t('mailWebhook.signingSecretSaved'))
}

const showClearModal = ref(false)
const clearing = ref(false)

/**
 * The deliberate way to stop sending. A save can no longer empty the list by accident, so this is
 * the only route that leaves nothing behind, and it asks first.
 */
async function clearProviders() {
  clearing.value = true
  try {
    await clearStationProviders()
    providers.value = []
    showClearModal.value = false
    handleSuccess(t('mailChain.cleared'))
  } catch {
    handleError(t('common.error'))
  } finally {
    clearing.value = false
  }
}

async function saveProviders() {
  if (!providersLoaded.value) return
  providers.value = await updateStationProviders(providers.value)
  handleSuccess(t('mailChain.saved'))
}

/**
 * Tries the stored provider rather than what is on screen, so the result says something about what
 * would actually carry the post. Anything unsaved has to be saved first to be tried.
 */
const testingPosition = ref<number | null>(null)
const testResults = ref<Record<number, {ok: boolean; message: string}>>({})

async function test(position: number, recipient: string) {
  testingPosition.value = position
  try {
    const result = await testStationProvider(position, recipient)
    testResults.value = {
      ...testResults.value,
      [position]: result.success
          ? {ok: true, message: t('mailChain.testOk', {position: position + 1, recipient})}
          : {ok: false, message: t('mailChain.testFailed', {position: position + 1, error: result.error ?? ''})},
    }
  } catch {
    testResults.value = {...testResults.value, [position]: {ok: false, message: t('common.error')}}
  } finally {
    testingPosition.value = null
  }
}
</script>

<template>
  <ViewContent
      :title="t('pages.station-mailing.title')"
      :subtitle="t('pages.station-mailing.subtitle')"
  >
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>
      <MailProviderChain
          v-model:providers="providers"
          :save="saveProviders"
          :default-recipient="ownAddress"
          :ready="providersLoaded"
          :testing-position="testingPosition"
          :test-results="testResults"
          show-display-fields
          @test="test"
          @clear="showClearModal = true"
      >
        <template #webhook="{provider, position}">
          <MailWebhookPanel
              :url="providers[position]?.deliveryWebhookUrl"
              :provider="provider"
              :regenerate="regenerateStationWebhookKey"
              :save-signing-secret="saveSigningSecret"
              :signing-secret-set="signingSecretSet"
          />
        </template>
      </MailProviderChain>
      <MailDashboard
          :load="getStationMailDashboard"
          :lift="liftStationBlock"
          :requeue="requeueStationStuckMails"
      />
      <MailProviderFreeTiers/>
      <ClearProvidersModal v-model="showClearModal" :clearing="clearing" @confirm="clearProviders"/>
    </div>
  </ViewContent>
</template>
