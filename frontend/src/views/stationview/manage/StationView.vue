/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SearchSelectInput from '@/components/input/select/SearchSelectInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import {stationManage} from '@/api'
import client from '@/api/client'

const {t} = useI18n()

const timezoneOptions = Intl.supportedValuesOf('timeZone').map(tz => ({value: tz, label: tz}))

const localeOptions = [
  {value: 'de-DE', label: 'Deutsch'},
  {value: 'en-US', label: 'English (US)'},
  {value: 'en-GB', label: 'English (UK)'},
  {value: 'fr-FR', label: 'Français'},
  {value: 'nl-NL', label: 'Nederlands'},
  {value: 'pl-PL', label: 'Polski'},
  {value: 'it-IT', label: 'Italiano'},
  {value: 'es-ES', label: 'Español'},
  {value: 'pt-PT', label: 'Português'},
  {value: 'da-DK', label: 'Dansk'},
  {value: 'sv-SE', label: 'Svenska'},
  {value: 'nb-NO', label: 'Norsk'},
]

const name = ref('')
const timezone = ref('Europe/Berlin')
const locale = ref('de-DE')
const hasLogo = ref(false)
const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const error = ref('')
const success = ref('')
const logoObjectUrl = ref<string | null>(null)

async function loadStation() {
  loading.value = true
  error.value = ''
  try {
    const info = await stationManage.getStationInfo()
    name.value = info.name ?? ''
    timezone.value = info.timezone ?? 'Europe/Berlin'
    locale.value = info.locale ?? 'de-DE'
    hasLogo.value = info.hasLogo
    if (info.hasLogo) {
      await loadLogoBlob()
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function loadLogoBlob() {
  try {
    const res = await client.get('/station/manage/logo', {
      responseType: 'blob',
      validateStatus: (status) => status === 200 || status === 404,
    })
    if (res.status === 404) {
      logoObjectUrl.value = null
      return
    }
    if (logoObjectUrl.value) URL.revokeObjectURL(logoObjectUrl.value)
    logoObjectUrl.value = URL.createObjectURL(res.data)
  } catch {
    logoObjectUrl.value = null
  }
}

async function saveName() {
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const info = await stationManage.updateStationName({
      name: name.value,
      timezone: timezone.value,
      locale: locale.value
    })
    name.value = info.name ?? ''
    success.value = t('stationManage.saved')
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

async function handleLogoUpload(file: File) {
  if (file.size > 2 * 1024 * 1024) {
    error.value = t('stationManage.logoTooLarge')
    return
  }

  uploading.value = true
  error.value = ''
  success.value = ''
  try {
    await stationManage.uploadLogo(file)
    hasLogo.value = true
    await loadLogoBlob()
    success.value = t('stationManage.logoUploaded')
  } catch {
    error.value = t('common.error')
  } finally {
    uploading.value = false
  }
}

async function removeLogo() {
  error.value = ''
  success.value = ''
  try {
    await stationManage.deleteLogo()
    hasLogo.value = false
    if (logoObjectUrl.value) {
      URL.revokeObjectURL(logoObjectUrl.value)
      logoObjectUrl.value = null
    }
    success.value = t('stationManage.logoDeleted')
  } catch {
    error.value = t('common.error')
  }
}

// -- Mail config --
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
const mailSaving = ref(false)
const mailTesting = ref(false)
const mailTestResult = ref<{success: boolean, error?: string | null} | null>(null)

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
  } catch { /* ignore */ }
}

async function saveMailConfig() {
  mailSaving.value = true
  error.value = ''
  success.value = ''
  mailTestResult.value = null
  try {
    const prov = mailProvider.value
    const providerNameMap: Record<string, string> = {
      RAPIDMAIL: 'RapidMail',
      TWILIO: 'Twilio',
      SWEEGO: 'Sweego',
      BREVO: 'Brevo',
    }
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
      providerName: providerNameMap[prov] ?? mailProviderName.value,
      providerUrl: providerUrlMap[prov] ?? mailProviderUrl.value,
      dailyLimit: mailDailyLimit.value,
      monthlyLimit: mailMonthlyLimit.value,
    })
    mailProvider.value = config.provider
    mailHasApiKey.value = config.hasApiKey
    mailSmtpPassword.value = ''
    mailApiKey.value = ''
    success.value = t('stationManage.mailSaved')
  } catch {
    error.value = t('common.error')
  } finally {
    mailSaving.value = false
  }
}

async function testMail() {
  mailTesting.value = true
  mailTestResult.value = null
  try {
    mailTestResult.value = await stationManage.testMailConfig()
  } catch {
    mailTestResult.value = { success: false, error: t('common.error') }
  } finally {
    mailTesting.value = false
  }
}

// -- Module settings --
const disabledModules = ref<Set<string>>(new Set())
const modulesSaving = ref(false)

const allModules = [
  {key: 'INVENTORY', label: 'moduleInventory'},
  {key: 'NEWS', label: 'moduleNews'},
  {key: 'EVENTS', label: 'moduleEvents'},
  {key: 'ATTENDANCE', label: 'moduleAttendance'},
  {key: 'FORMS', label: 'moduleForms'},
  {key: 'LOST_AND_FOUND', label: 'moduleLostAndFound'},
]

async function loadModules() {
  try {
    const res = await stationManage.getDisabledModules()
    disabledModules.value = new Set(res.disabledModules)
  } catch { /* ignore */ }
}

function isModuleEnabled(key: string): boolean {
  return !disabledModules.value.has(key)
}

async function toggleModule(key: string) {
  modulesSaving.value = true
  const next = new Set(disabledModules.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  try {
    const res = await stationManage.setDisabledModules([...next])
    disabledModules.value = new Set(res.disabledModules)
  } catch {
    error.value = t('common.error')
  }
  modulesSaving.value = false
}

onMounted(async () => {
  await loadStation()
  await Promise.all([loadMailConfig(), loadModules()])
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>

      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <NeutralContainer v-if="!loading" class="space-y-4">
        <SectionHeader>{{ t('stationManage.nameTitle') }}</SectionHeader>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('stationManage.name') }}</label>
          <TextInput v-model="name" :placeholder="t('stationManage.namePlaceholder')"/>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('stationManage.timezone') }}</label>
          <SearchSelectInput v-model="timezone" :options="timezoneOptions" :placeholder="t('stationManage.timezone')"/>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('stationManage.locale') }}</label>
          <SelectInput v-model="locale">
            <option v-for="opt in localeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </SelectInput>
        </div>
        <PrimaryButton :disabled="saving || !name" @click="saveName">
          {{ saving ? t('common.loading') : t('stationManage.save') }}
        </PrimaryButton>
      </NeutralContainer>

      <NeutralContainer v-if="!loading" class="space-y-4">
        <SectionHeader>{{ t('stationManage.logoTitle') }}</SectionHeader>

        <div v-if="hasLogo" class="space-y-3">
          <img
              v-if="logoObjectUrl"
              :src="logoObjectUrl"
              alt="Station Logo"
              class="max-h-32 max-w-64 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent object-contain"
          />
          <div class="flex items-center gap-2">
            <FileUploadButton :disabled="uploading" accept="image/png,image/jpeg,image/webp,image/svg+xml"
                              @select="handleLogoUpload">
              {{ uploading ? t('common.loading') : t('stationManage.changeLogo') }}
            </FileUploadButton>
            <DeleteButton @click="removeLogo"/>
          </div>
        </div>

        <div v-else class="space-y-3">
          <p class="text-sm text-(--text-muted)">{{ t('stationManage.noLogo') }}</p>
          <FileUploadButton :disabled="uploading" accept="image/png,image/jpeg,image/webp,image/svg+xml"
                            @select="handleLogoUpload">
            {{ uploading ? t('common.loading') : t('stationManage.uploadLogo') }}
          </FileUploadButton>
        </div>

        <p class="text-xs text-(--text-muted)">{{ t('stationManage.logoHint') }}</p>
      </NeutralContainer>

      <!-- Mail settings -->
      <NeutralContainer v-if="!loading" class="space-y-4">
        <SectionHeader>{{ t('stationManage.mailTitle') }}</SectionHeader>
        <p class="text-sm text-(--text-muted)">{{ t('stationManage.mailHint') }}</p>

        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('stationManage.mailProvider') }}</label>
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
              <label class="block text-sm font-medium">{{ t('stationManage.mailSenderAddress') }}</label>
              <TextInput v-model="mailSenderAddress" placeholder="noreply@example.com" />
            </div>
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('stationManage.mailSenderName') }}</label>
              <TextInput v-model="mailSenderName" placeholder="Ember" />
            </div>
          </div>

          <!-- Custom SMTP settings -->
          <template v-if="mailProvider === 'SMTP'">
            <SubHeader>SMTP</SubHeader>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-1">
                <label class="block text-sm font-medium">{{ t('stationManage.mailProviderName') }}</label>
                <TextInput v-model="mailProviderName" :placeholder="t('stationManage.mailProviderNamePlaceholder')" />
              </div>
              <div class="space-y-1">
                <label class="block text-sm font-medium">{{ t('stationManage.mailProviderUrl') }}</label>
                <TextInput v-model="mailProviderUrl" placeholder="https://example.com/privacy" />
              </div>
              <div class="space-y-1">
                <label class="block text-sm font-medium">{{ t('stationManage.mailSmtpHost') }}</label>
                <TextInput v-model="mailSmtpHost" placeholder="mail.example.com" />
              </div>
              <div class="space-y-1">
                <label class="block text-sm font-medium">{{ t('stationManage.mailSmtpPort') }}</label>
                <NumberInput v-model="mailSmtpPort" :min="1" :max="65535" />
              </div>
              <div class="space-y-1">
                <label class="block text-sm font-medium">{{ t('stationManage.mailSmtpUser') }}</label>
                <TextInput v-model="mailSmtpUser" placeholder="user@example.com" />
              </div>
              <div class="space-y-1">
                <label class="block text-sm font-medium">{{ t('stationManage.mailSmtpPassword') }}</label>
                <TextInput v-model="mailSmtpPassword" :placeholder="t('stationManage.mailPasswordPlaceholder')" type="password" />
              </div>
            </div>
            <div class="flex items-center justify-between">
              <label class="text-sm font-medium">SSL</label>
              <ToggleInput v-model="mailSmtpSsl" />
            </div>
          </template>

          <!-- RapidMail settings -->
          <template v-if="mailProvider === 'RAPIDMAIL'">
            <SubHeader>RapidMail</SubHeader>
            <p class="text-xs text-(--text-muted)">{{ t('stationManage.mailRapidmailHint') }}</p>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-1">
                <label class="block text-sm font-medium">{{ t('stationManage.mailSmtpUser') }}</label>
                <TextInput v-model="mailSmtpUser" placeholder="user@example.com" />
              </div>
              <div class="space-y-1">
                <label class="block text-sm font-medium">API Key</label>
                <TextInput v-model="mailApiKey" :placeholder="mailHasApiKey ? t('stationManage.mailApiKeyPlaceholder') : ''" type="password" />
              </div>
            </div>
          </template>

          <!-- Brevo settings -->
          <template v-if="mailProvider === 'BREVO'">
            <SubHeader>Brevo</SubHeader>
            <p class="text-xs text-(--text-muted)">{{ t('stationManage.mailBrevoHint') }}</p>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-1">
                <label class="block text-sm font-medium">{{ t('stationManage.mailSmtpUser') }}</label>
                <TextInput v-model="mailSmtpUser" placeholder="user@example.com" />
              </div>
              <div class="space-y-1">
                <label class="block text-sm font-medium">API Key</label>
                <TextInput v-model="mailApiKey" :placeholder="mailHasApiKey ? t('stationManage.mailApiKeyPlaceholder') : ''" type="password" />
              </div>
            </div>
          </template>

          <!-- Sweego settings -->
          <template v-if="mailProvider === 'SWEEGO'">
            <SubHeader>Sweego</SubHeader>
            <p class="text-xs text-(--text-muted)">{{ t('stationManage.mailSweegoHint') }}</p>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="space-y-1">
                <label class="block text-sm font-medium">{{ t('stationManage.mailSmtpUser') }}</label>
                <TextInput v-model="mailSmtpUser" placeholder="user@example.com" />
              </div>
              <div class="space-y-1">
                <label class="block text-sm font-medium">API Key</label>
                <TextInput v-model="mailApiKey" :placeholder="mailHasApiKey ? t('stationManage.mailApiKeyPlaceholder') : ''" type="password" />
              </div>
            </div>
          </template>

          <!-- Twilio settings -->
          <template v-if="mailProvider === 'TWILIO'">
            <SubHeader>Twilio</SubHeader>
            <p class="text-xs text-(--text-muted)">{{ t('stationManage.mailTwilioHint') }}</p>
            <div class="space-y-1">
              <label class="block text-sm font-medium">API Key</label>
              <TextInput v-model="mailApiKey" :placeholder="mailHasApiKey ? t('stationManage.mailApiKeyPlaceholder') : 'SG.xxxxx'" type="password" />
            </div>
          </template>
        </template>

        <!-- Limits -->
        <template v-if="mailProvider !== 'NONE'">
          <SubHeader>{{ t('stationManage.mailLimits') }}</SubHeader>
          <div class="grid gap-4 sm:grid-cols-2">
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('stationManage.mailDailyLimit') }}</label>
              <NumberInput v-model="mailDailyLimit" :min="1" :max="10000" />
              <p class="text-xs text-(--text-muted)">{{ t('stationManage.mailSentToday', { count: mailSentToday, limit: mailDailyLimit }) }}</p>
            </div>
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('stationManage.mailMonthlyLimit') }}</label>
              <NumberInput v-model="mailMonthlyLimit" :min="1" :max="100000" />
              <p class="text-xs text-(--text-muted)">{{ t('stationManage.mailSentMonth', { count: mailSentThisMonth, limit: mailMonthlyLimit }) }}</p>
            </div>
          </div>
        </template>

        <div class="flex items-center gap-2">
          <PrimaryButton :disabled="mailSaving" @click="saveMailConfig">
            {{ mailSaving ? t('common.loading') : t('stationManage.save') }}
          </PrimaryButton>
          <SuccessButton v-if="mailProvider !== 'NONE'" :disabled="mailTesting" @click="testMail">
            <font-awesome-icon :icon="['fas', 'plug']" class="mr-1" />
            {{ mailTesting ? t('common.loading') : t('stationManage.mailTest') }}
          </SuccessButton>
        </div>

        <Alert v-if="mailTestResult?.success" variant="success">{{ t('stationManage.mailTestSuccess') }}</Alert>
        <Alert v-if="mailTestResult && !mailTestResult.success" variant="error">
          {{ t('stationManage.mailTestFailed') }}: {{ mailTestResult.error }}
        </Alert>
      </NeutralContainer>

      <!-- Module settings -->
      <NeutralContainer v-if="!loading" class="space-y-4">
        <SectionHeader>{{ t('stationManage.modulesTitle') }}</SectionHeader>
        <p class="text-sm text-(--text-muted)">{{ t('stationManage.modulesHint') }}</p>
        <div class="space-y-3">
          <div v-for="mod in allModules" :key="mod.key" class="flex items-center justify-between">
            <span class="text-sm font-medium">{{ t(`stationManage.${mod.label}`) }}</span>
            <ToggleInput :model-value="isModuleEnabled(mod.key)" :disabled="modulesSaving" @update:model-value="toggleModule(mod.key)"/>
          </div>
        </div>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
