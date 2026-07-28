/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, nextTick, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SetupLayout from '@/views/stationview/setup/SetupLayout.vue'
import ThemeDefaultsPanel from '@/views/stationview/manage/stationthemeview/ThemeDefaultsPanel.vue'
import CustomColorsPanel from '@/views/stationview/manage/stationthemeview/CustomColorsPanel.vue'
import LogoSection from '@/views/stationview/manage/stationview/LogoSection.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {stationManage} from '@/api'
import {THEMES, type ModeColors, type ThemeColors} from '@/theme/themes'
import {useTheme} from '@/composables/useTheme'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {useAuthImage} from '@/composables/useAuthImage'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {nextStep, stepRouteName} from '@/views/stationview/setup/steps'

const {t} = useI18n()
const router = useRouter()
const {reload} = useSetupStatus()
const themeCtrl = useTheme()

const LOGO_MAX_SIZE = 2 * 1024 * 1024
const LOGO_URL = '/station/manage/logo?size=256'

const stationName = ref('')
const lockTheme = ref(false)
const lockFeel = ref(false)
const customEnabled = ref(false)
const presetKey = ref('')
const hasLogo = ref(false)
const logoUrl = ref<string | null>(null)
const {src: logoObjectUrl} = useAuthImage(logoUrl)
const loading = ref(true)
const error = ref('')

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

onMounted(async () => {
    try {
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
        }
        hasLogo.value = info.hasLogo
        if (info.hasLogo) logoUrl.value = LOGO_URL
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
})

const {running: uploading, error: logoError, run: handleLogoUpload} = useAsyncAction(async (file: File) => {
    await stationManage.uploadLogo(file)
    hasLogo.value = true
    logoUrl.value = null
    await nextTick()
    logoUrl.value = LOGO_URL
}, {formatError: () => t('fileUpload.uploadFailed')})

async function removeLogo() {
    try {
        await stationManage.deleteLogo()
        hasLogo.value = false
        logoUrl.value = null
    } catch {
        error.value = t('common.error')
    }
}

function removeCustomColors() {
    customEnabled.value = false
}

const {running: saving, error: saveError, run: runSave} = useAsyncAction(async () => {
    await stationManage.updateStationName({
        name: stationName.value,
        defaultTheme: themeCtrl.activeTheme.value,
        allowUserTheme: !lockTheme.value,
        defaultFeel: themeCtrl.activeFeel.value,
        allowUserFeel: !lockFeel.value,
        customThemeColors: customEnabled.value ? JSON.stringify(customColors.value) : null,
    })
    await reload()
    const next = nextStep('branding')
    if (next) router.push({name: stepRouteName(next)})
})

const displayError = computed(() => error.value || saveError.value)

function save() {
    error.value = ''
    return runSave()
}
</script>

<template>
  <SetupLayout step-id="branding" skippable :saving="saving" @save="save">
    <Alert v-if="displayError" variant="error">{{ displayError }}</Alert>
    <Spinner v-if="loading" size="lg"/>
    <template v-else>
      <ThemeDefaultsPanel v-model:lock-theme="lockTheme" v-model:lock-feel="lockFeel"/>
      <CustomColorsPanel
          v-model:enabled="customEnabled"
          v-model:colors="customColors"
          v-model:preset-key="presetKey"
          @load-preset="loadPreset"
          @remove="removeCustomColors"
      />
      <LogoSection
          :has-logo="hasLogo"
          :logo-object-url="logoObjectUrl"
          :uploading="uploading"
          :logo-error="logoError || null"
          :max-size="LOGO_MAX_SIZE"
          @upload="handleLogoUpload"
          @remove="removeLogo"
      />
    </template>
  </SetupLayout>
</template>
