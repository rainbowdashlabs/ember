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

onMounted(loadStation)
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
    </div>
  </ViewContent>
</template>
