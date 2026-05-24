/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
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
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {stationManage} from '@/api'
import client from '@/api/client'
import {THEMES} from '@/theme/themes'
import {useTheme} from '@/composables/useTheme'
import MailConfigSection from './stationview/MailConfigSection.vue'
import StationImportSection from './stationview/StationImportSection.vue'
import OwnerSection from './stationview/OwnerSection.vue'

const {t} = useI18n()

const timezoneOptions = Intl.supportedValuesOf('timeZone').map(tz => ({value: tz, label: tz}))

const themeOptions = Object.entries(THEMES).map(([key, theme]) => ({value: key, label: theme.label}))

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
const defaultTheme = ref('ember')
const allowUserTheme = ref(true)
const hasLogo = ref(false)
const isOwner = ref(false)
const stationId = ref('')
const ownerMemberId = ref<number | null>(null)
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
    defaultTheme.value = info.defaultTheme ?? 'ember'
    allowUserTheme.value = info.allowUserTheme ?? true
    hasLogo.value = info.hasLogo
    isOwner.value = info.isOwner
    stationId.value = info.id
    ownerMemberId.value = info.ownerMemberId ?? null
    publicKbModeValue.value = info.publicKbMode ?? 'OFF'
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

const themeSaving = ref(false)
const themeCtrl = useTheme()

// Live preview: apply theme when the dropdown changes
watch(defaultTheme, (newTheme) => {
  themeCtrl.applyTheme(newTheme)
})

async function saveTheme() {
  themeSaving.value = true
  error.value = ''
  success.value = ''
  try {
    const info = await stationManage.updateStationName({
      name: name.value,
      timezone: timezone.value,
      locale: locale.value,
      defaultTheme: defaultTheme.value,
      allowUserTheme: allowUserTheme.value,
    })
    defaultTheme.value = info.defaultTheme ?? 'ember'
    allowUserTheme.value = info.allowUserTheme ?? true
    // Apply the resolved theme: if user can override, use their theme; otherwise station default
    const resolvedTheme = allowUserTheme.value && themeCtrl.activeTheme.value !== defaultTheme.value
        ? themeCtrl.activeTheme.value
        : defaultTheme.value
    themeCtrl.applyTheme(resolvedTheme)
    success.value = t('theme.saved')
  } catch {
    error.value = t('common.error')
  } finally {
    themeSaving.value = false
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
  {key: 'WAITING_LIST', label: 'moduleWaitingList'},
  {key: 'QUIZ', label: 'moduleQuiz'},
  {key: 'KNOWLEDGE_BASE', label: 'moduleKnowledgeBase'},
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

// -- Public KB --
const publicKbModeValue = ref<string>('OFF')
const publicKbEnabled = computed(() => publicKbModeValue.value !== 'OFF')

async function togglePublicKb() {
  const newMode = publicKbEnabled.value ? 'OFF' : 'ALLOW_ALL'
  try {
    await stationManage.updateStationName({
      name: name.value,
      publicKbMode: newMode,
    })
    publicKbModeValue.value = newMode
    success.value = t('stationManage.saved')
  } catch { error.value = t('common.error') }
}

async function changePublicKbMode(mode: string | undefined) {
  if (!mode) return
  try {
    await stationManage.updateStationName({
      name: name.value,
      publicKbMode: mode,
    })
    publicKbModeValue.value = mode
  } catch { error.value = t('common.error') }
}

const publicKbUrl = computed(() => {
  if (!stationId.value) return ''
  return `${window.location.origin}/public/kb/${stationId.value}`
})

function handleError(msg: string) {
  error.value = msg
  success.value = ''
}

function handleSuccess(msg: string) {
  success.value = msg
  error.value = ''
}

onMounted(async () => {
  await loadStation()
  await loadModules()
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
      <MailConfigSection v-if="!loading" @error="handleError" @success="handleSuccess"/>

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

      <!-- Public Knowledge Base -->
      <NeutralContainer v-if="!loading" class="space-y-4">
        <SectionHeader>{{ t('stationManage.publicKb.title') }}</SectionHeader>
        <p class="text-sm text-(--text-muted)">{{ t('stationManage.publicKb.hint') }}</p>
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium">{{ t('stationManage.publicKb.enabled') }}</span>
          <ToggleInput :model-value="publicKbEnabled" @update:model-value="togglePublicKb"/>
        </div>
        <div v-if="publicKbEnabled" class="space-y-3">
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('stationManage.publicKb.mode') }}</label>
            <SelectInput :model-value="publicKbModeValue" @update:model-value="changePublicKbMode">
              <option value="ALLOW_ALL">{{ t('stationManage.publicKb.modeAllowAll') }}</option>
              <option value="DENY_ALL">{{ t('stationManage.publicKb.modeDenyAll') }}</option>
            </SelectInput>
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('stationManage.publicKb.publicUrl') }}</label>
            <code class="block rounded bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 text-sm break-all select-all">{{ publicKbUrl }}</code>
          </div>
        </div>
      </NeutralContainer>

      <!-- Theme settings -->
      <NeutralContainer v-if="!loading" class="space-y-4">
        <div class="flex items-center gap-2">
          <SectionHeader>{{ t('theme.stationTheme') }}</SectionHeader>
          <router-link :to="{name: 'help-station-theme-manage'}" target="_blank" class="text-[var(--text-muted)] hover:text-primary transition-colors">
            <font-awesome-icon :icon="['fas', 'circle-question']" class="w-4 h-4"/>
          </router-link>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium">{{ t('theme.stationDefaultTheme') }}</label>
          <SelectInput v-model="defaultTheme">
            <option v-for="opt in themeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </SelectInput>
          <p class="text-xs text-(--text-muted)">{{ t('theme.stationDefaultThemeHint') }}</p>
        </div>
        <div class="flex items-center justify-between">
          <div>
            <span class="text-sm font-medium">{{ t('theme.allowUserTheme') }}</span>
          </div>
          <ToggleInput v-model="allowUserTheme"/>
        </div>
        <PrimaryButton :disabled="themeSaving" @click="saveTheme">
          {{ themeSaving ? t('common.loading') : t('stationManage.save') }}
        </PrimaryButton>
      </NeutralContainer>

      <!-- Station import -->
      <StationImportSection v-if="!loading" @error="handleError" @success="handleSuccess"/>

      <!-- Owner sections (transfer, handover, deletion) -->
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
