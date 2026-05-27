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
import {THEMES} from '@/theme/themes'

const {t} = useI18n()

const dummyTheme = ref('ember')
const dummyAllowUser = ref(true)
const themeOptions = Object.entries(THEMES).map(([key, theme]) => ({value: key, label: theme.label}))
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
    </HelpSection>

    <HelpTip>{{ t('helpCenter.themeManage.tip') }}</HelpTip>
  </HelpArticle>
</template>
