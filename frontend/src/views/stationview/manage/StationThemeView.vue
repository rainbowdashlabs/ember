/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ThemeDefaultsPanel from './stationthemeview/ThemeDefaultsPanel.vue'
import CustomColorsPanel from './stationthemeview/CustomColorsPanel.vue'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {stationManage} from '@/api'
import {THEMES} from '@/theme/themes'
import type {ModeColors, ThemeColors} from '@/theme/themes'
import {useTheme} from '@/composables/useTheme'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {hasPermission, loaded} = useSession()
const router = useRouter()
watch(loaded, (isLoaded) => {
  if (isLoaded && !hasPermission(StationPermission.STATION_LOOK_AND_FEEL)) {
    router.replace('/station/dashboard/overview')
  }
}, {immediate: true})

const {t} = useI18n()
const themeCtrl = useTheme()

const stationName = ref('')

const lockTheme = ref(false)
const lockFeel = ref(false)

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

function loadPreset() {
  const theme = THEMES[presetKey.value]
  if (!theme) return
  customColors.value = JSON.parse(JSON.stringify(theme.colors)) as ThemeColors
}

const {loading, error} = useAsyncLoader(async () => {
  const info = await stationManage.getStationInfo()
  stationName.value = info.name ?? ''
  lockTheme.value = !(info.allowUserTheme ?? true)
  lockFeel.value = !(info.allowUserFeel ?? true)
  if (info.defaultTheme) themeCtrl.applyTheme(info.defaultTheme)
  if (info.customThemeColors) {
    try {
      customColors.value = JSON.parse(info.customThemeColors) as ThemeColors
      customEnabled.value = true
    } catch {
      customEnabled.value = false
    }
  } else {
    customEnabled.value = false
  }
})

async function save() {
  error.value = ''
  try {
    await stationManage.updateStationName({
      name: stationName.value,
      defaultTheme: themeCtrl.activeTheme.value,
      allowUserTheme: !lockTheme.value,
      defaultFeel: themeCtrl.activeFeel.value,
      allowUserFeel: !lockFeel.value,
      customThemeColors: customEnabled.value ? JSON.stringify(customColors.value) : null,
    })
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

function removeCustomColors() {
  customEnabled.value = false
  save()
}
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <ThemeDefaultsPanel v-model:lock-theme="lockTheme" v-model:lock-feel="lockFeel"/>
        <CustomColorsPanel
            v-model:enabled="customEnabled"
            v-model:colors="customColors"
            v-model:preset-key="presetKey"
            @load-preset="loadPreset"
            @remove="removeCustomColors"
        />
        <SaveButton :action="save"/>
      </template>
    </div>
  </ViewContent>
</template>
