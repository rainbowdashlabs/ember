/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import {Feel, THEMES} from '@/theme/themes'

const {t} = useI18n()

const props = defineProps<{
  save: () => Promise<void>
}>()

const defaultTheme = defineModel<string>('defaultTheme', {required: true})
const defaultFeel = defineModel<string>('defaultFeel', {required: true})
const lockFeel = defineModel<boolean>('lockFeel', {required: true})

const themeOptions = Object.entries(THEMES).map(([key, theme]) => ({value: key, label: theme.label}))
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('adminSettings.theme.title') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('adminSettings.theme.hint') }}</p>

    <div class="space-y-1">
      <FieldLabel>{{ t('adminSettings.theme.defaultTheme') }}</FieldLabel>
      <SelectInput v-model="defaultTheme" class="w-full">
        <option v-for="opt in themeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </SelectInput>
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('adminSettings.theme.defaultFeel') }}</FieldLabel>
      <SelectInput v-model="defaultFeel" class="w-full">
        <option :value="Feel.ROUNDED">{{ t('theme.feelROUNDED') }}</option>
        <option :value="Feel.CORNERS">{{ t('theme.feelCORNERS') }}</option>
      </SelectInput>
    </div>

    <div class="flex items-center justify-between">
      <div>
        <div class="font-medium">{{ t('adminSettings.theme.lockFeel') }}</div>
        <div class="text-sm text-(--text-muted)">{{ t('adminSettings.theme.lockFeelHint') }}</div>
      </div>
      <ToggleInput v-model="lockFeel"/>
    </div>

    <div class="flex justify-end">
      <SaveButton :action="props.save"/>
    </div>
  </NeutralContainer>
</template>
