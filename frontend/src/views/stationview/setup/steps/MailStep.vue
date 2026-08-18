/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SetupLayout from '@/views/stationview/setup/SetupLayout.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import MailProviderCredentialFields from '@/components/mail/MailProviderCredentialFields.vue'
import {emptyMailProvider, getStationProviders, updateStationProviders, type MailProvider} from '@/api/mailProviders'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {goToNextStep} from '@/views/stationview/setup/steps'
import {needsServerAddress} from '@/util/mailProviders'

const {t} = useI18n()
const router = useRouter()
const {reload} = useSetupStatus()

/**
 * Setting up asks for one provider, which becomes the first of the station's list. More can be
 * added later on the mail page; a station being set up has no use for a second one yet.
 */
const cfg = ref<MailProvider>({...emptyMailProvider(), provider: 'NONE'})
const loading = ref(true)

const relayUser = computed({
    get: () => cfg.value.smtpUser ?? '',
    set: (value: string) => { cfg.value.smtpUser = value },
})

const relaySecret = computed({
    get: () => cfg.value.apiKey ?? '',
    set: (value: string) => { cfg.value.apiKey = value },
})

const PROVIDERS = ['NONE', 'SMTP', 'RAPIDMAIL', 'TWILIO', 'SWEEGO', 'BREVO']

onMounted(async () => {
    try {
        const [existing] = await getStationProviders()
        if (existing) cfg.value = {...existing, smtpPassword: '', apiKey: ''}
    } catch { /* ignore */ }
    loading.value = false
})

const {running: saving, error, run: save} = useAsyncAction(async () => {
    await updateStationProviders(cfg.value.provider === 'NONE' ? [] : [cfg.value])
    await reload()
    goToNextStep(router, 'mail')
})
</script>

<template>
  <SetupLayout step-id="mail" skippable :saving="saving" @save="save">
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <InfoContainer class="space-y-1">
      <p class="font-medium text-sm">{{ t('setup.steps.mail.worksWithoutTitle') }}</p>
      <p class="text-sm">{{ t('setup.steps.mail.worksWithoutBody') }}</p>
    </InfoContainer>
    <p class="text-sm text-(--text-muted)">{{ t('setup.steps.mail.scopeNote') }}</p>
    <div v-if="!loading" class="space-y-3">
      <label class="block text-sm">
        {{ t('setup.steps.mail.provider') }}
        <SelectInput v-model="cfg.provider">
          <option v-for="p in PROVIDERS" :key="p" :value="p">{{ p }}</option>
        </SelectInput>
      </label>
      <label class="block text-sm">
        {{ t('setup.steps.mail.senderAddress') }}
        <TextInput v-model="cfg.senderAddress" type="email"/>
      </label>
      <label class="block text-sm">
        {{ t('setup.steps.mail.senderName') }}
        <TextInput v-model="cfg.senderName"/>
      </label>
      <template v-if="needsServerAddress(cfg.provider)">
        <label class="block text-sm">
          {{ t('setup.steps.mail.smtpHost') }}
          <TextInput v-model="cfg.smtpHost"/>
        </label>
        <label class="block text-sm">
          {{ t('setup.steps.mail.smtpPort') }}
          <NumberInput v-model="cfg.smtpPort"/>
        </label>
        <label class="flex items-center gap-2 text-sm">
          <ToggleInput v-model="cfg.smtpSsl"/>
          {{ t('setup.steps.mail.smtpSsl') }}
        </label>
        <label class="block text-sm">
          {{ t('setup.steps.mail.smtpUser') }}
          <TextInput v-model="cfg.smtpUser"/>
        </label>
        <label class="block text-sm">
          {{ t('setup.steps.mail.smtpPassword') }}
          <TextInput v-model="cfg.smtpPassword" type="password"/>
        </label>
      </template>
      <template v-else-if="cfg.provider !== 'NONE'">
        <MailProviderCredentialFields
            v-model:user="relayUser"
            v-model:secret="relaySecret"
            :provider="cfg.provider"
        />
      </template>
    </div>
  </SetupLayout>
</template>
