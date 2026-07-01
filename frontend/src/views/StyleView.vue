/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watchEffect} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'

import ViewContent from '@/components/layout/ViewContent.vue'
import ThemeSelector from '@/components/theme/ThemeSelector.vue'
import type {ThemeColors} from '@/theme/themes'
import {contrastTextColor, ensureContrast} from '@/theme/contrast'

import StylePrideText from '@/views/styleview/StylePrideText.vue'
import StyleLayeredLogo from '@/views/styleview/StyleLayeredLogo.vue'
import StyleTypography from '@/views/styleview/StyleTypography.vue'
import StyleButtons from '@/views/styleview/StyleButtons.vue'
import StyleInputs from '@/views/styleview/StyleInputs.vue'
import StyleBadges from '@/views/styleview/StyleBadges.vue'
import StyleContainers from '@/views/styleview/StyleContainers.vue'
import StyleTable from '@/views/styleview/StyleTable.vue'
import StyleFeedback from '@/views/styleview/StyleFeedback.vue'
import StyleScanner from '@/views/styleview/StyleScanner.vue'

const {t} = useI18n()
const route = useRoute()
const hasCustomParam = ref(false)

function applyCustomColors(colors: ThemeColors) {
  const isDark = document.documentElement.classList.contains('dark')
  const mode = isDark ? colors.dark : colors.light
  const root = document.documentElement.style
  root.setProperty('--color-primary', mode.primary)
  root.setProperty('--color-primary-accent', mode.primaryAccent)
  root.setProperty('--color-secondary', mode.secondary)
  root.setProperty('--color-secondary-accent', mode.secondaryAccent)
  root.setProperty('--color-info', mode.info)
  root.setProperty('--color-info-accent', mode.infoAccent)
  root.setProperty('--color-success', mode.success)
  root.setProperty('--color-error', mode.error)
  root.setProperty('--color-bg-light', colors.bgLight)
  root.setProperty('--color-bg-light-accent', colors.bgLightAccent)
  root.setProperty('--color-bg-dark', colors.bgDark)
  root.setProperty('--color-bg-dark-accent', colors.bgDarkAccent)
  root.setProperty('--color-primary-text', contrastTextColor(mode.primary))
  root.setProperty('--color-primary-accent-text', contrastTextColor(mode.primaryAccent))
  root.setProperty('--color-secondary-text', contrastTextColor(mode.secondary))
  root.setProperty('--color-secondary-accent-text', contrastTextColor(mode.secondaryAccent))
  root.setProperty('--color-info-text', contrastTextColor(mode.info))
  root.setProperty('--color-info-accent-text', contrastTextColor(mode.infoAccent))
  root.setProperty('--color-success-text', contrastTextColor(mode.success))
  root.setProperty('--color-error-text', contrastTextColor(mode.error))
  const pageBg = isDark ? colors.bgDark : colors.bgLight
  root.setProperty('--color-primary-badge', ensureContrast(mode.primaryAccent, pageBg))
  root.setProperty('--color-secondary-badge', ensureContrast(mode.secondaryAccent, pageBg))
  root.setProperty('--color-info-badge', ensureContrast(mode.infoAccent, pageBg))
  root.setProperty('--color-success-badge', ensureContrast(mode.success, pageBg))
  root.setProperty('--color-error-badge', ensureContrast(mode.error, pageBg))
}

watchEffect(() => {
  const param = route.query.customTheme as string | undefined
  if (param) {
    try {
      const colors = JSON.parse(decodeURIComponent(param)) as ThemeColors
      hasCustomParam.value = true
      setTimeout(() => applyCustomColors(colors), 50)
    } catch {
      void 0
    }
  }
}, { flush: 'post' })
</script>

<template>
  <ViewContent :title="t('pages.style.title')" :subtitle="t('pages.style.subtitle')">
    <div class="max-w-3xl mx-auto space-y-10 sm:space-y-12">
      <ThemeSelector/>

      <StylePrideText/>
      <StyleLayeredLogo/>
      <StyleTypography/>
      <StyleButtons/>
      <StyleInputs/>
      <StyleBadges/>
      <StyleContainers/>
      <StyleTable/>
      <StyleFeedback/>
      <StyleScanner/>
    </div>
  </ViewContent>
</template>
