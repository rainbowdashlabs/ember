/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'

const {hasPermission} = useSession()
const router = useRouter()
if (!hasPermission(StationPermission.STATION_LOOK_AND_FEEL)) {
  router.replace({name: 'dashboard-overview'})
}
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ThemeSelector from '@/components/theme/ThemeSelector.vue'
import {stationManage} from '@/api'
import {THEMES} from '@/theme/themes'
import type {ThemeColors, ModeColors} from '@/theme/themes'
import {useTheme} from '@/composables/useTheme'

const {t} = useI18n()
const themeCtrl = useTheme()

const themeOptions = Object.entries(THEMES).map(([key, theme]) => ({value: key, label: theme.label}))

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')
const stationName = ref('')

const lockTheme = ref(false)
const lockFeel = ref(false)

// Custom colors
const customEnabled = ref(false)
const presetKey = ref('')

function defaultColors(): ThemeColors {
  const mode: ModeColors = {
    primary: '#FF6421', primaryAccent: '#C71100',
    secondary: '#73CEFF', secondaryAccent: '#3694FF',
    info: '#c8ab03', infoAccent: '#af7501',
    success: '#00C507', error: '#ec2929',
  }
  return {
    light: {...mode}, dark: {...mode},
    bgLight: '#eaeaea', bgLightAccent: '#CFCFCF',
    bgDark: '#212121', bgDarkAccent: '#191919',
  }
}

const customColors = ref<ThemeColors>(defaultColors())

const modeColorFields: { key: keyof ModeColors; label: string }[] = [
  {key: 'primary', label: 'colorPrimary'},
  {key: 'primaryAccent', label: 'colorPrimaryAccent'},
  {key: 'secondary', label: 'colorSecondary'},
  {key: 'secondaryAccent', label: 'colorSecondaryAccent'},
  {key: 'info', label: 'colorInfo'},
  {key: 'infoAccent', label: 'colorInfoAccent'},
  {key: 'success', label: 'colorSuccess'},
  {key: 'error', label: 'colorError'},
]

const bgFields: { key: 'bgLight' | 'bgLightAccent' | 'bgDark' | 'bgDarkAccent'; label: string }[] = [
  {key: 'bgLight', label: 'colorBgLight'},
  {key: 'bgLightAccent', label: 'colorBgLightAccent'},
  {key: 'bgDark', label: 'colorBgDark'},
  {key: 'bgDarkAccent', label: 'colorBgDarkAccent'},
]

function loadPreset() {
  const theme = THEMES[presetKey.value]
  if (!theme) return
  customColors.value = JSON.parse(JSON.stringify(theme.colors)) as ThemeColors
}

async function loadStation() {
  loading.value = true
  error.value = ''
  try {
    const info = await stationManage.getStationInfo()
    stationName.value = info.name ?? ''
    lockTheme.value = !(info.allowUserTheme ?? true)
    lockFeel.value = !(info.allowUserFeel ?? true)
    // Apply the station's default theme/feel into the selector
    if (info.defaultTheme) themeCtrl.applyTheme(info.defaultTheme)
    if (info.customThemeColors) {
      try {
        customColors.value = JSON.parse(info.customThemeColors) as ThemeColors
        customEnabled.value = true
      } catch { /* ignore */ }
    } else {
      customEnabled.value = false
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    await stationManage.updateStationName({
      name: stationName.value,
      defaultTheme: themeCtrl.activeTheme.value,
      allowUserTheme: !lockTheme.value,
      defaultFeel: themeCtrl.activeFeel.value,
      allowUserFeel: !lockFeel.value,
      customThemeColors: customEnabled.value ? JSON.stringify(customColors.value) : null,
    })
    success.value = t('theme.saved')
    setTimeout(() => { success.value = '' }, 3000)
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

function removeCustomColors() {
  customEnabled.value = false
  save()
}

const previewUrl = computed(() => {
  const encoded = encodeURIComponent(JSON.stringify(customColors.value))
  return `/style?customTheme=${encoded}`
})

onMounted(loadStation)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading">
        <!-- Theme + Feel + Lock toggles -->
        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('theme.stationTheme') }}</SectionHeader>
          <p class="text-xs text-(--text-muted)">{{ t('theme.stationDefaultThemeHint') }}</p>
          <ThemeSelector
            v-model:lock-theme="lockTheme"
            v-model:lock-feel="lockFeel"
            :show-lock-theme="true"
            :show-lock-feel="true"
          />
        </NeutralContainer>

        <!-- Custom colors -->
        <NeutralContainer class="space-y-4">
          <div class="flex items-center justify-between">
            <SubHeader>{{ t('theme.customColors') }}</SubHeader>
            <ToggleInput v-model="customEnabled"/>
          </div>
          <p class="text-sm text-(--text-muted)">{{ t('theme.customColorsHint') }}</p>

          <template v-if="customEnabled">
            <!-- Load preset -->
            <div class="flex items-end gap-2">
              <div class="flex-1 space-y-1">
                <FieldLabel>{{ t('theme.loadPreset') }}</FieldLabel>
                <SelectInput v-model="presetKey">
                  <option value="" disabled>{{ t('theme.selectPreset') }}</option>
                  <option v-for="opt in themeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                </SelectInput>
              </div>
              <SecondaryButton :disabled="!presetKey" @click="loadPreset">{{ t('theme.applyPreset') }}</SecondaryButton>
            </div>

            <!-- Light mode colors -->
            <SubHeader class="!mt-4">{{ t('theme.lightModeColors') }}</SubHeader>
            <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
              <div v-for="field in modeColorFields" :key="'light-' + field.key" class="space-y-1">
                <FieldLabel class="text-xs">{{ t(`theme.${field.label}`) }}</FieldLabel>
                <div class="flex items-center gap-2">
                  <input
                    v-model="customColors.light[field.key]"
                    type="color"
                    class="h-9 w-12 rounded-theme border border-(--border) cursor-pointer bg-transparent"
                  />
                  <span class="text-xs text-(--text-muted) font-mono">{{ customColors.light[field.key] }}</span>
                </div>
              </div>
            </div>

            <!-- Dark mode colors -->
            <SubHeader class="!mt-4">{{ t('theme.darkModeColors') }}</SubHeader>
            <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
              <div v-for="field in modeColorFields" :key="'dark-' + field.key" class="space-y-1">
                <FieldLabel class="text-xs">{{ t(`theme.${field.label}`) }}</FieldLabel>
                <div class="flex items-center gap-2">
                  <input
                    v-model="customColors.dark[field.key]"
                    type="color"
                    class="h-9 w-12 rounded-theme border border-(--border) cursor-pointer bg-transparent"
                  />
                  <span class="text-xs text-(--text-muted) font-mono">{{ customColors.dark[field.key] }}</span>
                </div>
              </div>
            </div>

            <!-- Background colors -->
            <SubHeader class="!mt-4">{{ t('theme.backgrounds') }}</SubHeader>
            <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
              <div v-for="field in bgFields" :key="field.key" class="space-y-1">
                <FieldLabel class="text-xs">{{ t(`theme.${field.label}`) }}</FieldLabel>
                <div class="flex items-center gap-2">
                  <input
                    v-model="customColors[field.key]"
                    type="color"
                    class="h-9 w-12 rounded-theme border border-(--border) cursor-pointer bg-transparent"
                  />
                  <span class="text-xs text-(--text-muted) font-mono">{{ customColors[field.key] }}</span>
                </div>
              </div>
            </div>

            <div class="flex items-center gap-2">
              <a :href="previewUrl" target="_blank">
                <SecondaryButton :icon="['fas', 'eye']">{{ t('theme.preview') }}</SecondaryButton>
              </a>
              <DeleteButton @click="removeCustomColors"/>
            </div>
          </template>
        </NeutralContainer>

        <PrimaryButton :disabled="saving" @click="save">
          {{ saving ? t('common.loading') : t('stationManage.save') }}
        </PrimaryButton>
      </template>
    </div>
  </ViewContent>
</template>
