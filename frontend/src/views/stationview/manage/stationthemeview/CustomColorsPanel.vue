/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import ColorFieldGrid from './ColorFieldGrid.vue'
import {THEMES} from '@/theme/themes'
import type {ModeColors, ThemeColors} from '@/theme/themes'

const enabled = defineModel<boolean>('enabled', {required: true})
const colors = defineModel<ThemeColors>('colors', {required: true})
const presetKey = defineModel<string>('presetKey', {required: true})

const emit = defineEmits<{
  (e: 'load-preset'): void
  (e: 'remove'): void
}>()

const {t} = useI18n()

const themeOptions = Object.entries(THEMES).map(([key, theme]) => ({value: key, label: theme.label}))

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

const previewUrl = computed(() => {
  const encoded = encodeURIComponent(JSON.stringify(colors.value))
  return `/style?customTheme=${encoded}`
})

function onLightChange(key: keyof ModeColors, value: string) {
  colors.value = {...colors.value, light: {...colors.value.light, [key]: value}}
}

function onDarkChange(key: keyof ModeColors, value: string) {
  colors.value = {...colors.value, dark: {...colors.value.dark, [key]: value}}
}

function onBgChange(key: 'bgLight' | 'bgLightAccent' | 'bgDark' | 'bgDarkAccent', value: string) {
  colors.value = {...colors.value, [key]: value}
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('theme.customColors') }}</SubHeader>
      <ToggleInput v-model="enabled"/>
    </div>
    <p class="text-sm text-(--text-muted)">{{ t('theme.customColorsHint') }}</p>

    <template v-if="enabled">
      <div class="flex items-end gap-2">
        <div class="flex-1 space-y-1">
          <FieldLabel>{{ t('theme.loadPreset') }}</FieldLabel>
          <SelectInput v-model="presetKey">
            <option value="" disabled>{{ t('theme.selectPreset') }}</option>
            <option v-for="opt in themeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </SelectInput>
        </div>
        <SecondaryButton :disabled="!presetKey" @click="emit('load-preset')">
          {{ t('theme.applyPreset') }}
        </SecondaryButton>
      </div>

      <SubHeader class="!mt-4">{{ t('theme.lightModeColors') }}</SubHeader>
      <ColorFieldGrid
          :fields="modeColorFields"
          :values="colors.light"
          key-prefix="light"
          @change="onLightChange"
      />

      <SubHeader class="!mt-4">{{ t('theme.darkModeColors') }}</SubHeader>
      <ColorFieldGrid
          :fields="modeColorFields"
          :values="colors.dark"
          key-prefix="dark"
          @change="onDarkChange"
      />

      <SubHeader class="!mt-4">{{ t('theme.backgrounds') }}</SubHeader>
      <ColorFieldGrid
          :fields="bgFields"
          :values="colors"
          key-prefix="bg"
          @change="onBgChange"
      />

      <div class="flex items-center gap-2">
        <a :href="previewUrl" target="_blank">
          <SecondaryButton :icon="['fas', 'eye']">{{ t('theme.preview') }}</SecondaryButton>
        </a>
        <DeleteButton @click="emit('remove')"/>
      </div>
    </template>
  </NeutralContainer>
</template>
