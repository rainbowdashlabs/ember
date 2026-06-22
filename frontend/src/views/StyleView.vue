/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watchEffect} from 'vue'
import {useRoute} from 'vue-router'

import ThemeSelector from '@/components/theme/ThemeSelector.vue'
import type { ThemeColors } from '@/theme/themes'
import { contrastTextColor, ensureContrast } from '@/theme/contrast'

const route = useRoute()
const hasCustomParam = ref(false)
const lastScanned = ref('')
const lastContinuous = ref('')

const samplePatch = `--- a/notes.md
+++ b/notes.md
@@ -1,5 +1,6 @@
 # Meeting Notes
-Date: 2026-05-20
+Date: 2026-05-26
+Status: Final

-Action items are pending.
+All action items completed.
 Next meeting in two weeks.`

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

// Apply custom colors from query param — use watchEffect with post flush
// to ensure it runs after the theme system (initFromLocalStorage, fetchPublicTheme)
watchEffect(() => {
  const param = route.query.customTheme as string | undefined
  if (param) {
    try {
      const colors = JSON.parse(decodeURIComponent(param)) as ThemeColors
      hasCustomParam.value = true
      // Delay slightly to win the race against fetchPublicTheme
      setTimeout(() => applyCustomColors(colors), 50)
    } catch { /* ignore */ }
  }
}, { flush: 'post' })

import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'

import StyleTypography from '@/views/styleview/StyleTypography.vue'
import StyleButtons from '@/views/styleview/StyleButtons.vue'
import StyleInputs from '@/views/styleview/StyleInputs.vue'
import StyleBadges from '@/views/styleview/StyleBadges.vue'
import StyleContainers from '@/views/styleview/StyleContainers.vue'
import StyleTable from '@/views/styleview/StyleTable.vue'
import StyleFeedback from '@/views/styleview/StyleFeedback.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'

import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import type { EyeDirection } from '@/components/display/LayeredEmberLogo.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PrideText from '@/components/display/PrideText.vue'
import { emberLogo } from '@/composables/useEmberLogo'

const pridePreview = ref(true)
const logoShake = ref(false)

const { layers: logoLayers } = emberLogo()

const allEyeLayerNames = new Set([
  'fire_eyes_left', 'fire_eyes_left_half', 'fire_blink_left',
  'fire_eyes_mid', 'fire_eyes_mid_half', 'fire_blink',
  'fire_eyes_right', 'fire_eyes_right_half', 'fire_blink_right',
])

const eyeDirections = ['Left', 'Mid', 'Right'] as const
const eyeOpenness = ['Open', 'Half', 'Blink'] as const
const eyeMatrix: Record<string, Record<string, string>> = {
  Left:  { Open: 'fire_eyes_left',  Half: 'fire_eyes_left_half',  Blink: 'fire_blink_left' },
  Mid:   { Open: 'fire_eyes_mid',   Half: 'fire_eyes_mid_half',   Blink: 'fire_blink' },
  Right: { Open: 'fire_eyes_right', Half: 'fire_eyes_right_half', Blink: 'fire_blink_right' },
}

const decorationLayers: LogoLayer[] = [
  { name: 'fire_glow', label: 'Glow' },
  { name: 'fire_blush', label: 'Blush' },
]

const displayLayers: LogoLayer[] = [
  { name: 'fire_faq', label: 'FAQ' },
  { name: 'fire_woah_one', label: 'Woah 1' },
  { name: 'fire_woah_two', label: 'Woah 2' },
]

const activeLogoLayers = ref(new Set(['fire_glow', 'fire_blank', 'fire_eyes_mid']))
const logoAutoBlink = ref(false)
const logoDisplayShake = ref(true)
const logoGazePositions = ref<EyeDirection[]>([])

const gazeDirectionOptions: { key: EyeDirection; label: string }[] = [
  { key: 'left', label: 'Links' },
  { key: 'mid', label: 'Mitte' },
  { key: 'right', label: 'Rechts' },
]

function toggleGazePosition(dir: EyeDirection) {
  const idx = logoGazePositions.value.indexOf(dir)
  if (idx >= 0) {
    logoGazePositions.value.splice(idx, 1)
  } else {
    logoGazePositions.value.push(dir)
  }
}

function selectEye(fragment: string) {
  for (const eye of allEyeLayerNames) activeLogoLayers.value.delete(eye)
  if (fragment) activeLogoLayers.value.add(fragment)
}

function isEyeSelected(fragment: string): boolean {
  return activeLogoLayers.value.has(fragment)
}

function toggleDecoration(name: string) {
  if (activeLogoLayers.value.has(name)) {
    activeLogoLayers.value.delete(name)
  } else {
    activeLogoLayers.value.add(name)
  }
}

function toggleDisplay(name: string) {
  if (activeLogoLayers.value.has(name)) {
    activeLogoLayers.value.delete(name)
  } else {
    for (const d of displayLayerNames) activeLogoLayers.value.delete(d)
    activeLogoLayers.value.add(name)
  }
}
</script>

<template>
  <div class="max-w-3xl mx-auto px-4 py-6 sm:px-8 sm:py-8 space-y-10 sm:space-y-12">
    <!-- Theme Picker at top -->
    <div class="space-y-4">
      <PageHeader>Style Guide</PageHeader>
      <ThemeSelector/>
    </div>

    <!-- Pride Text -->
    <section class="space-y-4">
      <SectionHeader>Pride Text</SectionHeader>
      <div class="flex items-center gap-4 mb-2">
        <ToggleInput v-model="pridePreview"/>
        <span class="text-sm text-(--text-muted)">Aktiv</span>
      </div>
      <SubHeader>Text</SubHeader>
      <div class="flex flex-wrap items-baseline gap-6">
        <PrideText :active="pridePreview" class="text-4xl font-bold">Ember</PrideText>
        <PrideText :active="pridePreview" class="text-2xl font-bold">Ember</PrideText>
        <PrideText :active="pridePreview" class="text-lg font-bold">Ember</PrideText>
        <PrideText :active="pridePreview" class="text-sm font-semibold">Ember</PrideText>
      </div>
      <SubHeader>Banner</SubHeader>
      <div class="flex flex-wrap items-baseline gap-6">
        <PrideText :active="pridePreview" variant="banner" class="text-4xl font-bold">Ember</PrideText>
        <PrideText :active="pridePreview" variant="banner" class="text-2xl font-bold">Ember</PrideText>
        <PrideText :active="pridePreview" variant="banner" class="text-lg font-bold">Ember</PrideText>
        <PrideText :active="pridePreview" variant="banner" class="text-sm font-semibold">Ember</PrideText>
      </div>
      <PrideText :active="pridePreview" variant="banner" class="text-xl font-bold">Happy Pride Month!</PrideText>
    </section>

    <!-- Layered Ember Logo -->
    <section class="space-y-4">
      <SectionHeader>Layered Ember Logo</SectionHeader>
      <div class="flex flex-col sm:flex-row items-center gap-6">
        <LayeredEmberLogo
          :layers="logoLayers"
          :active-layers="activeLogoLayers"
          :auto-blink="logoAutoBlink"
          :gaze-positions="logoGazePositions"
          :bounce="logoShake"
          :display-shake="logoDisplayShake"
          size="w-48 h-48"
          :pixel-size="512"
          @update:active-layers="activeLogoLayers = $event"
        />
        <div class="space-y-3">
          <label class="flex items-center gap-2 mb-1">
            <ToggleInput v-model="logoAutoBlink"/>
            <span class="text-xs font-medium text-(--text-muted)">Blinzeln</span>
          </label>
          <label class="flex items-center gap-2 mb-1">
            <ToggleInput v-model="logoShake"/>
            <span class="text-xs font-medium text-(--text-muted)">Hüpfen</span>
          </label>
          <label class="flex items-center gap-2 mb-1">
            <ToggleInput v-model="logoDisplayShake"/>
            <span class="text-xs font-medium text-(--text-muted)">Wackeln (Anzeige)</span>
          </label>
          <div>
            <p class="text-xs font-medium text-(--text-muted) mb-1">Blickrichtung</p>
            <div class="flex flex-wrap gap-2">
              <SelectionToggleButton
                v-for="opt in gazeDirectionOptions"
                :key="opt.key"
                size="sm"
                :selected="logoGazePositions.includes(opt.key)"
                @toggle="toggleGazePosition(opt.key)"
              >
                {{ opt.label }}
              </SelectionToggleButton>
            </div>
            <p v-if="logoGazePositions.length === 1" class="text-xs text-(--text-muted) mt-1">
              Noch eine Richtung hinzufuegen um die Animation zu starten.
            </p>
          </div>
          <div :class="{ 'opacity-40 pointer-events-none': logoGazePositions.length >= 2 }">
            <p class="text-xs font-medium text-(--text-muted) mb-1">Augen</p>
            <div class="grid grid-cols-4 gap-1 text-xs">
              <div/>
              <div v-for="o in eyeOpenness" :key="o" class="text-center text-(--text-muted) font-medium py-1">{{ o }}</div>
              <template v-for="d in eyeDirections" :key="d">
                <div class="text-(--text-muted) font-medium flex items-center">{{ d }}</div>
                <SelectionToggleButton
                  v-for="o in eyeOpenness"
                  :key="eyeMatrix[d][o]"
                  size="sm"
                  :selected="isEyeSelected(eyeMatrix[d][o])"
                  @toggle="isEyeSelected(eyeMatrix[d][o]) ? selectEye('') : selectEye(eyeMatrix[d][o])"
                >
                  {{ o }}
                </SelectionToggleButton>
              </template>
            </div>
          </div>
          <div>
            <p class="text-xs font-medium text-(--text-muted) mb-1">Dekoration</p>
            <div class="flex flex-wrap gap-2">
              <SelectionToggleButton
                v-for="layer in decorationLayers"
                :key="layer.name"
                :selected="activeLogoLayers.has(layer.name)"
                @toggle="toggleDecoration(layer.name)"
              >
                {{ layer.label }}
              </SelectionToggleButton>
            </div>
          </div>
          <div>
            <p class="text-xs font-medium text-(--text-muted) mb-1">Anzeige</p>
            <div class="flex flex-wrap gap-2">
              <SelectionToggleButton
                v-for="layer in displayLayers"
                :key="layer.name"
                :selected="activeLogoLayers.has(layer.name)"
                @toggle="toggleDisplay(layer.name)"
              >
                {{ layer.label }}
              </SelectionToggleButton>
            </div>
          </div>
        </div>
      </div>
    </section>

    <StyleTypography/>
    <StyleButtons/>
    <StyleInputs/>
    <StyleBadges/>
    <StyleContainers/>
    <StyleTable/>
    <StyleFeedback/>

    <section class="space-y-4">
      <SectionHeader>Barcode Scanner</SectionHeader>
      <div class="flex items-center gap-3">
        <ScanButton @decoded="lastScanned = $event"/>
        <span class="text-sm text-(--text-muted)">{{ lastScanned ? `Decoded: ${lastScanned}` : 'No scan yet' }}</span>
      </div>
      <div class="flex items-center gap-3">
        <ScanButton mode="continuous" @decoded="lastContinuous = $event"/>
        <span class="text-sm text-(--text-muted)">{{ lastContinuous ? `Continuous decoded: ${lastContinuous}` : 'Continuous mode' }}</span>
      </div>
    </section>
  </div>
</template>
