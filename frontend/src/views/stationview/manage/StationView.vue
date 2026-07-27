/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAuthImages} from '@/composables/useAuthImage'

const {hasPermission, loaded} = useSession()
const router = useRouter()
watch(loaded, (isLoaded) => {
  if (isLoaded && !hasPermission(StationPermission.STATION_GENERAL)) {
    router.replace('/station/dashboard/overview')
  }
}, {immediate: true})
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {stationManage} from '@/api'
import OwnerSection from './stationview/OwnerSection.vue'
import LocationSection from './stationview/LocationSection.vue'
import GeneralSection from './stationview/GeneralSection.vue'
import LogoSection from './stationview/LogoSection.vue'

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
const isOwner = ref(false)
const stationId = ref('')
const ownerMemberId = ref<number | null>(null)
const success = ref('')
const {srcFor, load: loadLogoImage, revokeAll: revokeLogo} = useAuthImages<string>()

const {loading, error} = useAsyncLoader(async () => {
  const info = await stationManage.getStationInfo()
  name.value = info.name ?? ''
  timezone.value = info.timezone ?? 'Europe/Berlin'
  locale.value = info.locale ?? 'de-DE'
  hasLogo.value = info.hasLogo
  isOwner.value = info.isOwner
  stationId.value = info.id
  ownerMemberId.value = info.ownerMemberId ?? null
  if (info.hasLogo) {
    await loadLogoBlob()
  }
})

function loadLogoBlob() {
  return loadLogoImage('logo', '/station/manage/logo?size=256')
}

async function saveName() {
  error.value = ''
  try {
    const info = await stationManage.updateStationName({
      name: name.value,
      timezone: timezone.value,
      locale: locale.value
    })
    name.value = info.name ?? ''
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

const LOGO_MAX_SIZE = 2 * 1024 * 1024

const {running: uploading, error: logoError, run: handleLogoUpload} = useAsyncAction(
    async (file: File) => {
      success.value = ''
      await stationManage.uploadLogo(file)
      hasLogo.value = true
      await loadLogoBlob()
      success.value = t('stationManage.logoUploaded')
    },
    {formatError: () => t('fileUpload.uploadFailed')},
)

async function removeLogo() {
  error.value = ''
  success.value = ''
  try {
    await stationManage.deleteLogo()
    hasLogo.value = false
    revokeLogo()
    success.value = t('stationManage.logoDeleted')
  } catch {
    error.value = t('common.error')
  }
}

function handleError(msg: string) {
  error.value = msg
  success.value = ''
}

function handleSuccess(msg: string) {
  success.value = msg
  error.value = ''
}

</script>

<template>
  <ViewContent
      :title="t('pages.station-manage.title')"
      :subtitle="t('pages.station-manage.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>

      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <GeneralSection
          v-if="!loading"
          v-model:name="name"
          v-model:timezone="timezone"
          v-model:locale="locale"
          :timezone-options="timezoneOptions"
          :locale-options="localeOptions"
          :save-name="saveName"
      />

      <LogoSection
          v-if="!loading"
          :has-logo="hasLogo"
          :logo-object-url="srcFor('logo')"
          :uploading="uploading"
          :logo-error="logoError"
          :max-size="LOGO_MAX_SIZE"
          @upload="handleLogoUpload"
          @remove="removeLogo"
      />

      <LocationSection
          v-if="!loading"
          @error="handleError"
          @success="handleSuccess"
      />

      <OwnerSection
          v-if="!loading && isOwner"
          :station-id="stationId"
          :owner-member-id="ownerMemberId"
          @error="handleError"
          @success="handleSuccess"
          @owner-changed="isOwner = false"
      />
    </div>
  </ViewContent>
</template>
