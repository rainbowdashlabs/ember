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
import {activeModeVariables, applyVariables, backgroundVariables} from '@/theme/palette'

import StylePrideText from '@/views/styleview/StylePrideText.vue'
import StyleLayeredLogo from '@/views/styleview/StyleLayeredLogo.vue'
import StyleTypography from '@/views/styleview/StyleTypography.vue'
import StyleIcons from '@/views/styleview/StyleIcons.vue'
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
  applyVariables({...backgroundVariables(colors), ...activeModeVariables(colors, isDark)})
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
      <StyleIcons/>
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
