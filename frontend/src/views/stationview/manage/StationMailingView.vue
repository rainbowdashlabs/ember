/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Alert from '@/components/feedback/Alert.vue'
import MailConfigSection from './stationview/MailConfigSection.vue'
import MailFallbackChain from '@/components/mail/MailFallbackChain.vue'
import MailWebhookPanel from '@/components/mail/MailWebhookPanel.vue'
import MailProviderFreeTiers from '@/components/mail/MailProviderFreeTiers.vue'
import {
  getStationFallbacks,
  getStationWebhook,
  regenerateStationWebhookKey,
  saveStationSigningSecret,
  updateStationFallbacks,
  type MailFallback,
} from '@/api/mailFallbacks'
import {stationManage} from '@/api'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {hasPermission, loaded} = useSession()
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

const fallbacks = ref<MailFallback[]>([])
const webhookUrl = ref('')
const signingSecretSet = ref(false)
const provider = ref('')

onMounted(async () => {
  try {
    const [entries, webhook, config] = await Promise.all([
      getStationFallbacks(),
      getStationWebhook(),
      stationManage.getMailConfig(),
    ])
    fallbacks.value = entries
    webhookUrl.value = webhook.deliveryWebhookUrl
    signingSecretSet.value = webhook.signingSecretSet
    provider.value = config.provider
  } catch {
    handleError(t('common.error'))
  }
})

async function saveSigningSecret(secret: string) {
  const webhook = await saveStationSigningSecret(secret)
  signingSecretSet.value = webhook.signingSecretSet
  handleSuccess(t('mailWebhook.signingSecretSaved'))
}

async function saveFallbacks() {
  fallbacks.value = await updateStationFallbacks(fallbacks.value)
  handleSuccess(t('mailFallbacks.saved'))
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
      <MailConfigSection @error="handleError" @success="handleSuccess"/>
      <MailFallbackChain v-model:fallbacks="fallbacks" :save="saveFallbacks"/>
      <MailWebhookPanel
          :url="webhookUrl"
          :provider="provider"
          :regenerate="regenerateStationWebhookKey"
          :save-signing-secret="saveSigningSecret"
          :signing-secret-set="signingSecretSet"
      />
      <MailProviderFreeTiers/>
    </div>
  </ViewContent>
</template>
