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

import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import SecondaryContainer from '@/components/container/SecondaryContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import DiffView from '@/components/display/DiffView.vue'

import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import DownloadButton from '@/components/button/DownloadButton.vue'
import UploadButton from '@/components/button/UploadButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ConfirmButton from '@/components/button/ConfirmButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'

import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import ExchangeStatusBadge from '@/views/stationview/inventory/exchangeview/ExchangeStatusBadge.vue'
import { ExchangeStatus } from '@/api/types'

import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'

import StyleTypography from '@/views/styleview/StyleTypography.vue'
import StyleInputs from '@/views/styleview/StyleInputs.vue'
import StyleFeedback from '@/views/styleview/StyleFeedback.vue'

import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import type { EyeDirection } from '@/components/display/LayeredEmberLogo.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PrideText from '@/components/display/PrideText.vue'
import { emberLogo } from '@/composables/useEmberLogo'

const toggleStates = ref(new Set([1, 3]))
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

    <!-- Buttons -->
    <section class="space-y-4">
      <SectionHeader>Buttons</SectionHeader>
      <div class="flex flex-wrap gap-2 items-center">
        <PrimaryButton>Primary</PrimaryButton>
        <SecondaryButton>Secondary</SecondaryButton>
        <SuccessButton>Success</SuccessButton>
        <ErrorButton>Error</ErrorButton>
        <InfoButton>Info</InfoButton>
      </div>
      <div class="flex flex-wrap gap-2 items-center">
        <PrimaryButton :icon="['fas', 'plus']">With Icon</PrimaryButton>
        <SecondaryButton :icon="['fas', 'download']">Download</SecondaryButton>
        <SuccessButton :icon="['fas', 'check']">Confirm</SuccessButton>
        <ErrorButton :icon="['fas', 'trash']">Delete</ErrorButton>
      </div>
      <div class="flex flex-wrap gap-2 items-center">
        <PrimaryButton compact>Compact</PrimaryButton>
        <SecondaryButton compact>Compact</SecondaryButton>
        <SuccessButton compact>Compact</SuccessButton>
      </div>
      <div class="flex flex-wrap gap-2 items-center"><SaveButton :action="() => new Promise(r => setTimeout(r, 800))"/><SaveButton :action="() => new Promise(r => setTimeout(r, 800))">Custom text</SaveButton></div>
      <div class="flex flex-wrap gap-2 items-center">
        <PrimaryButton disabled>Disabled</PrimaryButton>
        <SecondaryButton disabled>Disabled</SecondaryButton>
        <SuccessButton disabled>Disabled</SuccessButton>
        <ErrorButton disabled>Disabled</ErrorButton>
        <InfoButton disabled>Disabled</InfoButton>
      </div>
      <div class="flex flex-wrap gap-2 items-center">
        <LinkButton>Link Button</LinkButton>
        <LinkButton disabled>Link (disabled)</LinkButton>
      </div>
    </section>

    <!-- Icon Buttons -->
    <section class="space-y-4">
      <SectionHeader>Icon Buttons</SectionHeader>
      <div class="flex flex-wrap gap-2 items-center">
        <IconButton :icon="['fas', 'gear']" label="Settings"/>
        <DownloadButton/>
        <UploadButton/>
        <DeleteButton/>
        <ConfirmButton/>
        <EditButton/>
      </div>
      <div class="flex flex-wrap gap-2 items-center">
        <IconButton :icon="['fas', 'gear']" label="Settings" disabled/>
        <DownloadButton disabled/>
        <UploadButton disabled/>
        <DeleteButton disabled/>
        <ConfirmButton disabled/>
        <EditButton disabled/>
      </div>
    </section>

    <!-- Selection Toggle & Dropdown -->
    <section class="space-y-4">
      <SectionHeader>Selection Toggle Buttons</SectionHeader>
      <div class="flex flex-wrap gap-2">
        <SelectionToggleButton
            v-for="i in 5"
            :key="i"
            :selected="toggleStates.has(i)"
            @toggle="toggleStates.has(i) ? toggleStates.delete(i) : toggleStates.add(i)"
        >
          Option {{ i }}
        </SelectionToggleButton>
      </div>
    </section>

    <section class="space-y-4">
      <SectionHeader>Dropdown Menu Items</SectionHeader>
      <NeutralContainer class="p-0! max-w-xs overflow-hidden">
        <DropdownMenuItem :icon="['fas', 'folder']" icon-class="text-(--accent)">Neuer Ordner</DropdownMenuItem>
        <DropdownMenuItem :icon="['fas', 'file-lines']">Neue Datei</DropdownMenuItem>
        <DropdownMenuItem :icon="['fas', 'upload']">Hochladen</DropdownMenuItem>
        <DropdownMenuItem :icon="['fab', 'youtube']" icon-class="text-red-600">YouTube</DropdownMenuItem>
      </NeutralContainer>
    </section>

    <StyleInputs/>

    <!-- Badges -->
    <section class="space-y-4">
      <SectionHeader>Badges</SectionHeader>
      <div class="flex flex-wrap gap-2 items-center">
        <PrimaryBadge>Primary</PrimaryBadge>
        <SecondaryBadge>Secondary</SecondaryBadge>
        <SuccessBadge>Success</SuccessBadge>
        <ErrorBadge>Error</ErrorBadge>
        <InfoBadge>Info</InfoBadge>
        <SizeBadge>M</SizeBadge>
        <SizeBadge lost>M (lost)</SizeBadge>
        <StationBadge station-name="DLRG Musterstadt" />
      </div>
      <SubHeader>Exchange Status</SubHeader>
      <div class="flex flex-wrap gap-2 items-center">
        <ExchangeStatusBadge :status="ExchangeStatus.ANNOUNCED" />
        <ExchangeStatusBadge :status="ExchangeStatus.RECEIVED" />
        <ExchangeStatusBadge :status="ExchangeStatus.SHIPPED" />
        <ExchangeStatusBadge :status="ExchangeStatus.ARRIVED" />
        <ExchangeStatusBadge :status="ExchangeStatus.DONE" />
      </div>
    </section>

    <!-- Containers -->
    <section class="space-y-4">
      <SectionHeader>Containers</SectionHeader>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <PrimaryContainer>Primary container</PrimaryContainer>
        <SecondaryContainer>Secondary container</SecondaryContainer>
        <SuccessContainer>Success container</SuccessContainer>
        <ErrorContainer>Error container</ErrorContainer>
        <InfoContainer>Info container</InfoContainer>
        <NeutralContainer>Neutral container</NeutralContainer>
      </div>
    </section>

    <!-- Table -->
    <section class="space-y-4">
      <SectionHeader>Table</SectionHeader>
      <NeutralContainer :padded="false" class="overflow-x-auto">
        <table class="w-full text-sm">
          <THead>
            <Th>Name</Th>
            <Th>PermissionGrant</Th>
            <Th align="right">Score</Th>
          </THead>
          <tbody>
            <TRow>
              <Td>Alice</Td>
              <Td>Admin</Td>
              <Td align="right">95</Td>
            </TRow>
            <TRow>
              <Td>Bob</Td>
              <Td muted>Member</Td>
              <Td align="right">72</Td>
            </TRow>
            <TRow>
              <Td>Charlie</Td>
              <Td muted>Guest</Td>
              <Td align="right" muted>—</Td>
            </TRow>
          </tbody>
        </table>
      </NeutralContainer>
    </section>

    <section>
      <SectionHeader>Diff View</SectionHeader>
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <NeutralContainer class="!p-0 overflow-hidden">
          <p class="px-3 py-2 text-sm font-medium border-b border-(--border)">Compact (no line numbers)</p>
          <DiffView :patch="samplePatch" compact/>
        </NeutralContainer>
        <NeutralContainer class="!p-0 overflow-hidden">
          <p class="px-3 py-2 text-sm font-medium border-b border-(--border)">With line numbers</p>
          <DiffView :patch="samplePatch" show-line-numbers/>
        </NeutralContainer>
      </div>
    </section>

    <StyleFeedback/>
  </div>
</template>
