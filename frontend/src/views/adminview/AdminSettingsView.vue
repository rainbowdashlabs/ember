/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import { adminSettings } from '@/api'
import type { AuthConfig, MailingConfig } from '@/api/adminSettings'
import MutedText from '@/components/typography/MutedText.vue'

const { t } = useI18n()

const loading = ref(true)
const error = ref('')
const success = ref('')
const registrationEnabled = ref(true)

// Auth config
const authConfig = ref<AuthConfig>({
  tokenBytes: 32,
  verifyTokenHours: 24,
  passwordTokenHours: 72,
  sessionMinutes: 30,
})

// Mailing config
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

// Legal documents
const legalTypes = ['privacy', 'tos', 'consent', 'imprint'] as const
type LegalType = (typeof legalTypes)[number]
const activeLegalTab = ref<LegalType>('privacy')
const legalContent = ref('')
const legalVersion = ref('')
const legalLoading = ref(false)
const legalSaving = ref(false)

async function loadSettings() {
  loading.value = true
  error.value = ''
  try {
    const [settings, auth, mailing] = await Promise.all([
      adminSettings.getSettings(),
      adminSettings.getAuthConfig(),
      adminSettings.getMailingConfig(),
    ])
    registrationEnabled.value = settings.stationRegistrationEnabled
    authConfig.value = auth
    mailingConfig.value = mailing
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function toggleRegistration(value: boolean) {
  error.value = ''
  success.value = ''
  try {
    const result = await adminSettings.updateSettings({ stationRegistrationEnabled: value })
    registrationEnabled.value = result.stationRegistrationEnabled
    showSuccess()
  } catch {
    error.value = t('common.error')
    registrationEnabled.value = !value
  }
}

async function saveAuthConfig() {
  error.value = ''
  success.value = ''
  try {
    const result = await adminSettings.updateAuthConfig(authConfig.value)
    authConfig.value = result
    showSuccess()
  } catch {
    error.value = t('common.error')
  }
}

async function saveMailingConfig() {
  error.value = ''
  success.value = ''
  try {
    const result = await adminSettings.updateMailingConfig(mailingConfig.value)
    mailingConfig.value = result
    showSuccess()
  } catch {
    error.value = t('common.error')
  }
}

async function loadLegalDocument(type: LegalType) {
  legalLoading.value = true
  legalContent.value = ''
  legalVersion.value = ''
  try {
    const doc = await adminSettings.getLegalDocument(type)
    legalContent.value = doc.content
    legalVersion.value = doc.version
  } catch {
    error.value = t('common.error')
  } finally {
    legalLoading.value = false
  }
}

async function saveLegalDocument() {
  legalSaving.value = true
  error.value = ''
  success.value = ''
  try {
    const doc = await adminSettings.updateLegalDocument(activeLegalTab.value, legalContent.value)
    legalVersion.value = doc.version
    showSuccess()
  } catch {
    error.value = t('common.error')
  } finally {
    legalSaving.value = false
  }
}

function showSuccess() {
  success.value = t('adminSettings.saved')
  setTimeout(() => {
    success.value = ''
  }, 3000)
}

watch(activeLegalTab, (type) => {
  loadLegalDocument(type)
})

onMounted(async () => {
  await loadSettings()
  await loadLegalDocument(activeLegalTab.value)
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading">
        <!-- General Settings -->
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('adminSettings.title') }}</SectionHeader>

          <div class="flex items-center justify-between">
            <div>
              <div class="font-medium">{{ t('adminSettings.stationRegistration') }}</div>
              <div class="text-sm text-(--text-muted)">
                {{ t('adminSettings.stationRegistrationHint') }}
              </div>
            </div>
            <ToggleInput :model-value="registrationEnabled" @update:model-value="toggleRegistration" />
          </div>
        </NeutralContainer>

        <!-- Auth Settings -->
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('adminSettings.auth.title') }}</SectionHeader>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <FieldLabel class="mb-1">{{ t('adminSettings.auth.tokenBytes') }}</FieldLabel>
              <NumberInput v-model="authConfig.tokenBytes" />
              <MutedText tag="div" class="mt-1">{{ t('adminSettings.auth.tokenBytesHint') }}</MutedText>
            </div>
            <div>
              <FieldLabel class="mb-1">{{
                t('adminSettings.auth.verifyTokenHours')
              }}</FieldLabel>
              <NumberInput v-model="authConfig.verifyTokenHours" />
              <MutedText tag="div" class="mt-1">
                {{ t('adminSettings.auth.verifyTokenHoursHint') }}
              </MutedText>
            </div>
            <div>
              <FieldLabel class="mb-1">{{
                t('adminSettings.auth.passwordTokenHours')
              }}</FieldLabel>
              <NumberInput v-model="authConfig.passwordTokenHours" />
              <MutedText tag="div" class="mt-1">
                {{ t('adminSettings.auth.passwordTokenHoursHint') }}
              </MutedText>
            </div>
            <div>
              <FieldLabel class="mb-1">{{
                t('adminSettings.auth.sessionMinutes')
              }}</FieldLabel>
              <NumberInput v-model="authConfig.sessionMinutes" />
              <MutedText tag="div" class="mt-1">
                {{ t('adminSettings.auth.sessionMinutesHint') }}
              </MutedText>
            </div>
          </div>

          <div class="flex justify-end">
            <PrimaryButton @click="saveAuthConfig">{{ t('adminSettings.legal.save') }}</PrimaryButton>
          </div>
        </NeutralContainer>

        <!-- Mailing Settings -->
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('adminSettings.mailing.title') }}</SectionHeader>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <FieldLabel class="mb-1">{{ t('adminSettings.mailing.provider') }}</FieldLabel>
              <SelectInput v-model="mailingConfig.provider">
                <option v-for="p in mailProviders" :key="p" :value="p">{{ p }}</option>
              </SelectInput>
            </div>
            <div>
              <FieldLabel class="mb-1">{{
                t('adminSettings.mailing.senderAddress')
              }}</FieldLabel>
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
              <FieldLabel class="mb-1">{{
                t('adminSettings.mailing.dailySendLimit')
              }}</FieldLabel>
              <NumberInput v-model="mailingConfig.dailySendLimit" />
              <MutedText tag="div" class="mt-1">
                {{ t('adminSettings.mailing.dailySendLimitHint') }}
              </MutedText>
            </div>
            <div>
              <FieldLabel class="mb-1">{{
                t('adminSettings.mailing.notificationDigestIntervalMinutes')
              }}</FieldLabel>
              <NumberInput v-model="mailingConfig.notificationDigestIntervalMinutes" />
              <MutedText tag="div" class="mt-1">
                {{ t('adminSettings.mailing.notificationDigestIntervalMinutesHint') }}
              </MutedText>
            </div>
          </div>

          <!-- SMTP sub-section -->
          <div class="border-t border-(--border) pt-4 mt-4">
            <SubHeader>{{ t('adminSettings.mailing.smtp.title') }}</SubHeader>
            <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mt-3">
              <div>
                <FieldLabel class="mb-1">{{
                  t('adminSettings.mailing.smtp.host')
                }}</FieldLabel>
                <TextInput v-model="mailingConfig.smtpHost" />
              </div>
              <div>
                <FieldLabel class="mb-1">{{
                  t('adminSettings.mailing.smtp.port')
                }}</FieldLabel>
                <NumberInput v-model="mailingConfig.smtpPort" />
              </div>
              <div class="flex items-center gap-2 pt-6">
                <ToggleInput v-model="mailingConfig.smtpSsl" />
                <span class="text-sm font-medium">{{ t('adminSettings.mailing.smtp.ssl') }}</span>
              </div>
            </div>
          </div>

          <div class="flex justify-end">
            <PrimaryButton @click="saveMailingConfig">{{ t('adminSettings.legal.save') }}</PrimaryButton>
          </div>
        </NeutralContainer>

        <!-- Legal Documents -->
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('adminSettings.legal.title') }}</SectionHeader>

          <!-- Tabs -->
          <div class="flex gap-2 flex-wrap">
            <SecondaryButton
              v-for="type in legalTypes"
              :key="type"
              :class="{ 'ring-2 ring-(--accent)': activeLegalTab === type }"
              @click="activeLegalTab = type"
            >
              {{ t(`adminSettings.legal.${type}`) }}
            </SecondaryButton>
          </div>

          <Spinner v-if="legalLoading" size="md" />

          <template v-if="!legalLoading">
            <div v-if="legalVersion" class="text-xs text-(--text-muted)">
              {{ t('adminSettings.legal.version') }}: {{ legalVersion }}
            </div>
            <MarkdownEditor
              v-model="legalContent"
              :placeholder="t('adminSettings.legal.contentPlaceholder')"
            />
            <div class="flex justify-end">
              <PrimaryButton :disabled="legalSaving" @click="saveLegalDocument">
                {{ t('adminSettings.legal.save') }}
              </PrimaryButton>
            </div>
          </template>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
