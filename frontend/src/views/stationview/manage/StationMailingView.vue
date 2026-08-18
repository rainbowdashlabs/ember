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
import MailWebhookPanel from '@/components/mail/MailWebhookPanel.vue'
import MailProviderFreeTiers from '@/components/mail/MailProviderFreeTiers.vue'
import {
  getStationProviders,
  getStationWebhook,
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

onMounted(async () => {
  try {
    const [entries, webhook] = await Promise.all([getStationProviders(), getStationWebhook()])
    providers.value = entries
    signingSecretSet.value = webhook.signingSecretSet
  } catch {
    handleError(t('common.error'))
  }
})

async function saveSigningSecret(secret: string) {
  const webhook = await saveStationSigningSecret(secret)
  signingSecretSet.value = webhook.signingSecretSet
  handleSuccess(t('mailWebhook.signingSecretSaved'))
}

async function saveProviders() {
  providers.value = await updateStationProviders(providers.value)
  handleSuccess(t('mailChain.saved'))
}

/**
 * Tries the stored provider rather than what is on screen, so the result says something about what
 * would actually carry the post. Anything unsaved has to be saved first to be tried.
 */
async function test(position: number, recipient: string) {
  try {
    const result = await testStationProvider(position, recipient)
    if (result.success) handleSuccess(t('mailChain.testOk', {position: position + 1, recipient}))
    else handleError(t('mailChain.testFailed', {position: position + 1, error: result.error ?? ''}))
  } catch {
    handleError(t('common.error'))
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
          show-display-fields
          @test="test"
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
      <MailProviderFreeTiers/>
    </div>
  </ViewContent>
</template>
