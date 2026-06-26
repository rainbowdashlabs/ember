/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'

import SectionHeader from '@/components/typography/SectionHeader.vue'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import type {EyeDirection, LogoLayer} from '@/components/display/LayeredEmberLogo.vue'
import {emberLogo} from '@/composables/useEmberLogo'

import LogoAnimationToggles from '@/views/styleview/LogoAnimationToggles.vue'
import LogoGazeControls from '@/views/styleview/LogoGazeControls.vue'
import LogoEyeMatrix from '@/views/styleview/LogoEyeMatrix.vue'
import LogoLayerToggleGroup from '@/views/styleview/LogoLayerToggleGroup.vue'

const {layers: logoLayers} = emberLogo()

const allEyeLayerNames = new Set([
  'fire_eyes_left', 'fire_eyes_left_half', 'fire_blink_left',
  'fire_eyes_mid', 'fire_eyes_mid_half', 'fire_blink',
  'fire_eyes_right', 'fire_eyes_right_half', 'fire_blink_right',
])

const decorationLayers: LogoLayer[] = [
  { name: 'fire_glow', label: 'Glow' },
  { name: 'fire_blush', label: 'Blush' },
]

const displayLayers: LogoLayer[] = [
  { name: 'fire_faq', label: 'FAQ' },
  { name: 'fire_woah_one', label: 'Woah 1' },
  { name: 'fire_woah_two', label: 'Woah 2' },
]

const displayLayerNames = displayLayers.map(l => l.name)

const activeLogoLayers = ref(new Set(['fire_glow', 'fire_blank', 'fire_eyes_mid']))
const logoAutoBlink = ref(false)
const logoShake = ref(false)
const logoDisplayShake = ref(true)
const logoGazePositions = ref<EyeDirection[]>([])

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
        <LogoAnimationToggles
          v-model:auto-blink="logoAutoBlink"
          v-model:shake="logoShake"
          v-model:display-shake="logoDisplayShake"
        />
        <LogoGazeControls
          :positions="logoGazePositions"
          @toggle="toggleGazePosition"
        />
        <LogoEyeMatrix
          :disabled="logoGazePositions.length >= 2"
          :is-selected="isEyeSelected"
          @select="selectEye"
        />
        <LogoLayerToggleGroup
          label="Dekoration"
          :layers="decorationLayers"
          :active="activeLogoLayers"
          @toggle="toggleDecoration"
        />
        <LogoLayerToggleGroup
          label="Anzeige"
          :layers="displayLayers"
          :active="activeLogoLayers"
          @toggle="toggleDisplay"
        />
      </div>
    </div>
  </section>
</template>
