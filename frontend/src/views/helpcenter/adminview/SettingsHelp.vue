/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminSettings.title')" :subtitle="t('helpCenter.adminSettings.subtitle')">
    <HelpSection :title="t('helpCenter.adminSettings.registrationTitle')">
      <p>{{ t('helpCenter.adminSettings.registrationText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminSettings.themeTitle')">
      <p>{{ t('helpCenter.adminSettings.themeText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminSettings.authTitle')">
      <p>{{ t('helpCenter.adminSettings.authText') }}</p>
    </HelpSection>

    <!-- Dummy: Settings page -->
    <HelpSection :title="t('helpCenter.adminSettings.exampleTitle')">
      <!-- General settings -->
      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('adminSettings.title') }}</SectionHeader>

        <div class="flex items-center justify-between">
          <div>
            <div class="font-medium">{{ t('adminSettings.stationRegistration') }}</div>
            <div class="text-sm text-(--text-muted)">{{ t('adminSettings.stationRegistrationHint') }}</div>
          </div>
          <ToggleInput :model-value="true" />
        </div>

        <div class="flex items-center justify-between">
          <div>
            <div class="font-medium">{{ t('adminSettings.forcePrideFlag') }}</div>
            <div class="text-sm text-(--text-muted)">{{ t('adminSettings.forcePrideFlagHint') }}</div>
          </div>
          <ToggleInput :model-value="false" />
        </div>
      </NeutralContainer>

      <!-- Theme settings -->
      <NeutralContainer class="space-y-4 mt-4">
        <SectionHeader>{{ t('adminSettings.theme.title') }}</SectionHeader>
        <p class="text-sm text-(--text-muted)">{{ t('adminSettings.theme.hint') }}</p>

        <div class="space-y-1">
          <FieldLabel>{{ t('adminSettings.theme.defaultTheme') }}</FieldLabel>
          <SelectInput :model-value="'ember'" class="w-full">
            <option value="ember">Ember</option>
          </SelectInput>
        </div>

        <div class="space-y-1">
          <FieldLabel>{{ t('adminSettings.theme.defaultFeel') }}</FieldLabel>
          <SelectInput :model-value="'ROUNDED'" class="w-full">
            <option value="ROUNDED">{{ t('theme.feelROUNDED') }}</option>
            <option value="CORNERS">{{ t('theme.feelCORNERS') }}</option>
          </SelectInput>
        </div>

        <div class="flex items-center justify-between">
          <div>
            <div class="font-medium">{{ t('adminSettings.theme.lockFeel') }}</div>
            <div class="text-sm text-(--text-muted)">{{ t('adminSettings.theme.lockFeelHint') }}</div>
          </div>
          <ToggleInput :model-value="false" />
        </div>

        <div class="flex justify-end">
          <PrimaryButton>{{ t('adminSettings.legal.save') }}</PrimaryButton>
        </div>
      </NeutralContainer>

      <!-- Auth settings -->
      <NeutralContainer class="space-y-4 mt-4">
        <SectionHeader>{{ t('adminSettings.auth.title') }}</SectionHeader>

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <FieldLabel class="mb-1">{{ t('adminSettings.auth.tokenBytes') }}</FieldLabel>
            <NumberInput :model-value="32" />
            <MutedText tag="div" class="mt-1">{{ t('adminSettings.auth.tokenBytesHint') }}</MutedText>
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('adminSettings.auth.verifyTokenHours') }}</FieldLabel>
            <NumberInput :model-value="24" />
            <MutedText tag="div" class="mt-1">{{ t('adminSettings.auth.verifyTokenHoursHint') }}</MutedText>
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('adminSettings.auth.passwordTokenHours') }}</FieldLabel>
            <NumberInput :model-value="72" />
            <MutedText tag="div" class="mt-1">{{ t('adminSettings.auth.passwordTokenHoursHint') }}</MutedText>
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('adminSettings.auth.sessionMinutes') }}</FieldLabel>
            <NumberInput :model-value="30" />
            <MutedText tag="div" class="mt-1">{{ t('adminSettings.auth.sessionMinutesHint') }}</MutedText>
          </div>
        </div>

        <div class="flex justify-end">
          <PrimaryButton>{{ t('adminSettings.legal.save') }}</PrimaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminSettings.tip') }}</HelpTip>
  </HelpArticle>
</template>
