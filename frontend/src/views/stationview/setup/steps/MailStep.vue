/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
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
import {stationManage} from '@/api'
import type {MailConfigRequest} from '@/api/stationManage'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {nextStep, stepRouteName} from '@/views/stationview/setup/steps'

const {t} = useI18n()
const router = useRouter()
const {reload} = useSetupStatus()

const cfg = ref<MailConfigRequest>({
    provider: 'NONE',
    smtpHost: '',
    smtpPort: 587,
    smtpSsl: false,
    smtpUser: '',
    smtpPassword: '',
    senderAddress: '',
    senderName: '',
    apiKey: '',
})
const loading = ref(true)

const PROVIDERS = ['NONE', 'SMTP', 'RAPIDMAIL', 'TWILIO', 'SWEEGO', 'BREVO']

onMounted(async () => {
    try {
        const existing = await stationManage.getMailConfig()
        cfg.value = {
            provider: existing.provider,
            smtpHost: existing.smtpHost,
            smtpPort: existing.smtpPort,
            smtpSsl: existing.smtpSsl,
            smtpUser: existing.smtpUser,
            smtpPassword: '',
            senderAddress: existing.senderAddress,
            senderName: existing.senderName,
            apiKey: '',
        }
    } catch { /* ignore */ }
    loading.value = false
})

const {running: saving, error, run: save} = useAsyncAction(async () => {
    await stationManage.updateMailConfig(cfg.value)
    await reload()
    const next = nextStep('mail')
    if (next) router.push({name: stepRouteName(next)})
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
      <template v-if="cfg.provider === 'SMTP'">
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
            v-model:user="cfg.smtpUser"
            v-model:secret="cfg.apiKey"
            :provider="cfg.provider"
        />
      </template>
    </div>
  </SetupLayout>
</template>
