/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {THEMES} from '@/theme/themes'

const {t} = useI18n()

const dummyTheme = ref('ember')
const dummyAllowUser = ref(true)
const dummyCustomEnabled = ref(true)
const themeOptions = Object.entries(THEMES).map(([key, theme]) => ({value: key, label: theme.label}))

const dummyColors = [
  {label: 'Primary', hex: '#FF6421'},
  {label: 'Primary Accent', hex: '#C71100'},
  {label: 'Secondary', hex: '#73CEFF'},
  {label: 'Secondary Accent', hex: '#3694FF'},
  {label: 'Info', hex: '#c8ab03'},
  {label: 'Info Accent', hex: '#af7501'},
  {label: 'Success', hex: '#00C507'},
  {label: 'Error', hex: '#ec2929'},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.themeManage.title')" :subtitle="t('helpCenter.themeManage.subtitle')">
    <HelpSection :title="t('helpCenter.themeManage.whatIs')">
      <p>{{ t('helpCenter.themeManage.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.themeManage.defaultTitle')">
      <p>{{ t('helpCenter.themeManage.defaultText') }}</p>
    </HelpSection>

    <!-- Dummy: Theme management panel -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('theme.stationTheme') }}</SectionHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('theme.stationDefaultTheme') }}</FieldLabel>
        <SelectInput v-model="dummyTheme" class="w-full">
          <option v-for="opt in themeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </SelectInput>
        <p class="text-xs text-(--text-muted)">{{ t('theme.stationDefaultThemeHint') }}</p>
      </div>
      <div class="flex items-center justify-between">
        <div>
          <span class="text-sm font-medium">{{ t('theme.allowUserTheme') }}</span>
        </div>
        <ToggleInput v-model="dummyAllowUser"/>
      </div>
      <PrimaryButton disabled>{{ t('stationManage.save') }}</PrimaryButton>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.themeManage.allowTitle')">
      <p>{{ t('helpCenter.themeManage.allowText') }}</p>
      <p>{{ t('helpCenter.themeManage.allowOnText') }}</p>
      <p>{{ t('helpCenter.themeManage.allowOffText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.themeManage.customTitle')">
      <p>{{ t('helpCenter.themeManage.customText') }}</p>
      <p>{{ t('helpCenter.themeManage.customText2') }}</p>
    </HelpSection>

    <!-- Dummy: Custom colors section -->
    <NeutralContainer class="space-y-4">
      <div class="flex items-center justify-between">
        <SubHeader>{{ t('theme.customColors') }}</SubHeader>
        <ToggleInput v-model="dummyCustomEnabled"/>
      </div>
      <p class="text-sm text-(--text-muted)">{{ t('theme.customColorsHint') }}</p>

      <template v-if="dummyCustomEnabled">
        <div class="flex items-end gap-2">
          <div class="flex-1 space-y-1">
            <FieldLabel>{{ t('theme.loadPreset') }}</FieldLabel>
            <SelectInput model-value="" disabled>
              <option value="" disabled>{{ t('theme.selectPreset') }}</option>
              <option v-for="opt in themeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </SelectInput>
          </div>
          <SecondaryButton disabled>{{ t('theme.applyPreset') }}</SecondaryButton>
        </div>

        <SubHeader class="!mt-4">{{ t('theme.lightModeColors') }}</SubHeader>
        <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
          <div v-for="color in dummyColors" :key="color.label" class="space-y-1">
            <FieldLabel class="text-xs">{{ color.label }}</FieldLabel>
            <div class="flex items-center gap-2">
              <div class="h-9 w-12 rounded-theme border border-(--border)" :style="{backgroundColor: color.hex}"/>
              <span class="text-xs text-(--text-muted) font-mono">{{ color.hex }}</span>
            </div>
          </div>
        </div>

        <div class="flex items-center gap-2">
          <SecondaryButton :icon="['fas', 'eye']" disabled>{{ t('theme.preview') }}</SecondaryButton>
          <DeleteButton disabled/>
        </div>
      </template>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.themeManage.tip') }}</HelpTip>
  </HelpArticle>
</template>
