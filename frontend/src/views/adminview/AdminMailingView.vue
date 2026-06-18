/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import { adminSettings } from '@/api'
import type { MailingConfig } from '@/api/adminSettings'
import MutedText from '@/components/typography/MutedText.vue'

const { t } = useI18n()

const loading = ref(true)
const error = ref('')

const mailingConfig = ref<MailingConfig>({
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
})

const mailProviders = ['NONE', 'SMTP', 'RAPIDMAIL', 'TWILIO', 'SWEEGO', 'BREVO']

async function loadMailingConfig() {
  loading.value = true
  error.value = ''
  try {
    mailingConfig.value = await adminSettings.getMailingConfig()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function saveMailingConfig() {
  error.value = ''
  try {
    const result = await adminSettings.updateMailingConfig(mailingConfig.value)
    mailingConfig.value = result
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

onMounted(async () => {
  await loadMailingConfig()
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('adminSettings.mailing.title') }}</SectionHeader>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <FieldLabel class="mb-1">{{ t('adminSettings.mailing.provider') }}</FieldLabel>
              <SelectInput v-model="mailingConfig.provider" class="w-full">
                <option v-for="p in mailProviders" :key="p" :value="p">{{ p }}</option>
              </SelectInput>
            </div>
            <div>
              <FieldLabel class="mb-1">{{ t('adminSettings.mailing.senderAddress') }}</FieldLabel>
              <TextInput v-model="mailingConfig.senderAddress" />
            </div>
            <div>
              <FieldLabel class="mb-1">{{ t('adminSettings.mailing.senderName') }}</FieldLabel>
              <TextInput v-model="mailingConfig.senderName" />
            </div>
            <div>
              <FieldLabel class="mb-1">{{ t('adminSettings.mailing.user') }}</FieldLabel>
              <TextInput v-model="mailingConfig.user" />
            </div>
            <div>
              <FieldLabel class="mb-1">{{ t('adminSettings.mailing.password') }}</FieldLabel>
              <TextInput v-model="mailingConfig.password" />
            </div>
            <div>
              <FieldLabel class="mb-1">{{ t('adminSettings.mailing.apiKey') }}</FieldLabel>
              <TextInput v-model="mailingConfig.apiKey" />
            </div>
            <div>
              <FieldLabel class="mb-1">{{ t('adminSettings.mailing.dailySendLimit') }}</FieldLabel>
              <NumberInput v-model="mailingConfig.dailySendLimit" />
              <MutedText tag="div" class="mt-1">{{ t('adminSettings.mailing.dailySendLimitHint') }}</MutedText>
            </div>
            <div>
              <FieldLabel class="mb-1">{{ t('adminSettings.mailing.notificationDigestIntervalMinutes') }}</FieldLabel>
              <NumberInput v-model="mailingConfig.notificationDigestIntervalMinutes" />
              <MutedText tag="div" class="mt-1">{{ t('adminSettings.mailing.notificationDigestIntervalMinutesHint') }}</MutedText>
            </div>
          </div>

          <!-- SMTP sub-section -->
          <div class="border-t border-(--border) pt-4 mt-4">
            <SubHeader>{{ t('adminSettings.mailing.smtp.title') }}</SubHeader>
            <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mt-3">
              <div>
                <FieldLabel class="mb-1">{{ t('adminSettings.mailing.smtp.host') }}</FieldLabel>
                <TextInput v-model="mailingConfig.smtpHost" />
              </div>
              <div>
                <FieldLabel class="mb-1">{{ t('adminSettings.mailing.smtp.port') }}</FieldLabel>
                <NumberInput v-model="mailingConfig.smtpPort" />
              </div>
              <div class="flex items-center gap-2 pt-6">
                <ToggleInput v-model="mailingConfig.smtpSsl" />
                <span class="text-sm font-medium">{{ t('adminSettings.mailing.smtp.ssl') }}</span>
              </div>
            </div>
          </div>

          <div class="flex justify-end">
            <SaveButton :action="saveMailingConfig"/>
          </div>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
