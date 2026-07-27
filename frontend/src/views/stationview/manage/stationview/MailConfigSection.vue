/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import MailProviderCredentialFields from '@/components/mail/MailProviderCredentialFields.vue'
import {RELAY_PROVIDER_NAMES} from '@/util/mailProviders'
import {stationManage} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'

const emit = defineEmits<{
  error: [msg: string]
  success: [msg: string]
}>()

const {t} = useI18n()

const mailProvider = ref('NONE')
const mailSmtpHost = ref('')
const mailSmtpPort = ref(587)
const mailSmtpSsl = ref(false)
const mailSmtpUser = ref('')
const mailSmtpPassword = ref('')
const mailSenderAddress = ref('')
const mailSenderName = ref('')
const mailApiKey = ref('')
const mailHasApiKey = ref(false)
const mailProviderName = ref('')
const mailProviderUrl = ref('')
const mailDailyLimit = ref(100)
const mailMonthlyLimit = ref(2000)
const mailSentToday = ref(0)
const mailSentThisMonth = ref(0)
interface MailTestResult {
  success: boolean
  error?: string | null
}

const mailTestResult = ref<MailTestResult | null>(null)
const showClearModal = ref(false)

async function loadMailConfig() {
  try {
    const config = await stationManage.getMailConfig()
    mailProvider.value = config.provider
    mailSmtpHost.value = config.smtpHost
    mailSmtpPort.value = config.smtpPort
    mailSmtpSsl.value = config.smtpSsl
    mailSmtpUser.value = config.smtpUser
    mailSenderAddress.value = config.senderAddress
    mailSenderName.value = config.senderName
    mailHasApiKey.value = config.hasApiKey
    mailProviderName.value = config.providerName
    mailProviderUrl.value = config.providerUrl
    mailDailyLimit.value = config.dailyLimit
    mailMonthlyLimit.value = config.monthlyLimit
    mailSentToday.value = config.sentToday
    mailSentThisMonth.value = config.sentThisMonth
    mailSmtpPassword.value = ''
    mailApiKey.value = ''
  } catch {
    return
  }
}

async function saveMailConfig() {
  mailTestResult.value = null
  try {
    const prov = mailProvider.value
    const providerUrlMap: Record<string, string> = {
      RAPIDMAIL: 'https://www.rapidmail.com/data-protection',
      TWILIO: 'https://www.twilio.com/en-us/legal/privacy',
      SWEEGO: 'https://www.sweego.io/data-privacy-agreement-dpa',
      BREVO: 'https://www.brevo.com/de/legal/privacypolicy/',
    }
    const config = await stationManage.updateMailConfig({
      provider: prov,
      smtpHost: mailSmtpHost.value,
      smtpPort: mailSmtpPort.value,
      smtpSsl: mailSmtpSsl.value,
      smtpUser: mailSmtpUser.value,
      smtpPassword: mailSmtpPassword.value || undefined,
      senderAddress: mailSenderAddress.value,
      senderName: mailSenderName.value,
      apiKey: mailApiKey.value || undefined,
      providerName: RELAY_PROVIDER_NAMES[prov] ?? mailProviderName.value,
      providerUrl: providerUrlMap[prov] ?? mailProviderUrl.value,
      dailyLimit: mailDailyLimit.value,
      monthlyLimit: mailMonthlyLimit.value,
    })
    mailProvider.value = config.provider
    mailHasApiKey.value = config.hasApiKey
    mailSmtpPassword.value = ''
    mailApiKey.value = ''
    emit('success', t('stationManage.mailSaved'))
  } catch (e) {
    const backendMessage = (e as {response?: {data?: {title?: string; message?: string}}})?.response?.data?.title
        ?? (e as {response?: {data?: {message?: string}}})?.response?.data?.message
    emit('error', backendMessage ? t('stationManage.mailSaveFailed', {error: backendMessage}) : t('common.error'))
    throw e
  }
}

const {running: clearing, error: clearError, run: runClearMailConfig} = useAsyncAction(async () => {
  await stationManage.clearMailConfig()
  mailProvider.value = 'NONE'
  mailSmtpHost.value = ''
  mailSmtpPort.value = 587
  mailSmtpSsl.value = false
  mailSmtpUser.value = ''
  mailSmtpPassword.value = ''
  mailSenderAddress.value = ''
  mailSenderName.value = ''
  mailApiKey.value = ''
  mailHasApiKey.value = false
  mailProviderName.value = ''
  mailProviderUrl.value = ''
  mailTestResult.value = null
  showClearModal.value = false
  emit('success', t('stationManage.mailCleared'))
})

async function clearMailConfig() {
  await runClearMailConfig()
  if (clearError.value) emit('error', clearError.value)
}

const {running: mailTesting, error: testError, run: runTestMail} = useAsyncAction(async () => {
  mailTestResult.value = await stationManage.testMailConfig()
})

async function testMail() {
  mailTestResult.value = null
  await runTestMail()
  if (testError.value) mailTestResult.value = {success: false, error: testError.value}
}

const {running: sendingTestMail, error: sendTestError, run: runSendTestMail} = useAsyncAction(async () => {
  await stationManage.sendTestMail()
  emit('success', t('stationManage.mailTestMailSent'))
})

async function sendTestMail() {
  await runSendTestMail()
  if (sendTestError.value) emit('error', sendTestError.value)
}

onMounted(loadMailConfig)
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center gap-2">
      <SectionHeader>{{ t('stationManage.mailTitle') }}</SectionHeader>
      <router-link :to="{name: 'help-station-mailing'}" target="_blank" class="text-[var(--text-muted)] hover:text-primary transition-colors">
        <font-awesome-icon :icon="['fas', 'circle-question']" class="w-4 h-4"/>
      </router-link>
    </div>
    <p class="text-sm text-(--text-muted)">{{ t('stationManage.mailHint') }}</p>

    <div class="space-y-1">
      <FieldLabel>{{ t('stationManage.mailProvider') }}</FieldLabel>
      <SelectInput v-model="mailProvider">
        <option value="NONE">{{ t('stationManage.mailProviderNone') }}</option>
        <option value="RAPIDMAIL">RapidMail</option>
        <option value="TWILIO">Twilio</option>
        <option value="SWEEGO">Sweego</option>
        <option value="BREVO">Brevo</option>
        <option value="SMTP">{{ t('stationManage.mailProviderCustomSmtp') }}</option>
      </SelectInput>
    </div>

    <template v-if="mailProvider !== 'NONE'">
      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1">
          <FieldLabel>{{ t('stationManage.mailSenderAddress') }}</FieldLabel>
          <TextInput v-model="mailSenderAddress" placeholder="noreply@example.com" />
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('stationManage.mailSenderName') }}</FieldLabel>
          <TextInput v-model="mailSenderName" placeholder="Ember" />
        </div>
      </div>

      <!-- Custom SMTP settings -->
      <template v-if="mailProvider === 'SMTP'">
        <SubHeader>SMTP</SubHeader>
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="space-y-1">
            <FieldLabel>{{ t('stationManage.mailProviderName') }}</FieldLabel>
            <TextInput v-model="mailProviderName" :placeholder="t('stationManage.mailProviderNamePlaceholder')" />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('stationManage.mailProviderUrl') }}</FieldLabel>
            <TextInput v-model="mailProviderUrl" placeholder="https://example.com/privacy" />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('stationManage.mailSmtpHost') }}</FieldLabel>
            <TextInput v-model="mailSmtpHost" placeholder="mail.example.com" />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('stationManage.mailSmtpPort') }}</FieldLabel>
            <NumberInput v-model="mailSmtpPort" :min="1" :max="65535" />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('stationManage.mailSmtpUser') }}</FieldLabel>
            <TextInput v-model="mailSmtpUser" placeholder="user@example.com" />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('stationManage.mailSmtpPassword') }}</FieldLabel>
            <TextInput v-model="mailSmtpPassword" :placeholder="t('stationManage.mailPasswordPlaceholder')" type="password" />
          </div>
        </div>
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium">SSL</label>
          <ToggleInput v-model="mailSmtpSsl" />
        </div>
      </template>

      <!-- Relay provider credentials -->
      <template v-if="RELAY_PROVIDER_NAMES[mailProvider]">
        <SubHeader>{{ RELAY_PROVIDER_NAMES[mailProvider] }}</SubHeader>
        <MailProviderCredentialFields
            v-model:user="mailSmtpUser"
            v-model:secret="mailApiKey"
            :provider="mailProvider"
            :secret-placeholder="mailHasApiKey ? t('stationManage.mailApiKeyPlaceholder') : undefined"
        />
      </template>
    </template>

    <!-- Limits -->
    <template v-if="mailProvider !== 'NONE'">
      <SubHeader>{{ t('stationManage.mailLimits') }}</SubHeader>
      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1">
          <FieldLabel>{{ t('stationManage.mailDailyLimit') }}</FieldLabel>
          <NumberInput v-model="mailDailyLimit" :min="1" :max="10000" />
          <p class="text-xs text-(--text-muted)">{{ t('stationManage.mailSentToday', {count: mailSentToday, limit: mailDailyLimit}) }}</p>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('stationManage.mailMonthlyLimit') }}</FieldLabel>
          <NumberInput v-model="mailMonthlyLimit" :min="1" :max="100000" />
          <p class="text-xs text-(--text-muted)">{{ t('stationManage.mailSentMonth', {count: mailSentThisMonth, limit: mailMonthlyLimit}) }}</p>
        </div>
      </div>
    </template>

    <div class="flex items-center gap-2 flex-wrap">
      <SaveButton :action="saveMailConfig"/>
      <SuccessButton :icon="['fas', 'plug']" v-if="mailProvider !== 'NONE'" :disabled="mailTesting" @click="testMail">
        {{ mailTesting ? t('common.loading') : t('stationManage.mailTest') }}
      </SuccessButton>
      <SecondaryButton :icon="['fas', 'paper-plane']" v-if="mailProvider !== 'NONE'" :disabled="sendingTestMail" @click="sendTestMail">
        {{ sendingTestMail ? t('common.loading') : t('stationManage.mailTestMail') }}
      </SecondaryButton>
      <ErrorButton :icon="['fas', 'trash']" :disabled="clearing" @click="showClearModal = true">
        {{ t('stationManage.mailClear') }}
      </ErrorButton>
    </div>

    <Alert v-if="mailTestResult?.success" variant="success">{{ t('stationManage.mailTestSuccess') }}</Alert>
    <Alert v-if="mailTestResult && !mailTestResult.success" variant="error">
      {{ t('stationManage.mailTestFailed') }}: {{ mailTestResult.error }}
    </Alert>

    <Modal v-model="showClearModal">
      <div class="space-y-4">
        <p>{{ t('stationManage.mailClearConfirm') }}</p>
        <div class="flex justify-end gap-3">
          <SecondaryButton :disabled="clearing" @click="showClearModal = false">{{ t('common.cancel') }}</SecondaryButton>
          <ErrorButton :icon="['fas', 'trash']" :disabled="clearing" @click="clearMailConfig">
            {{ t('stationManage.mailClear') }}
          </ErrorButton>
        </div>
      </div>
    </Modal>
  </NeutralContainer>
</template>
