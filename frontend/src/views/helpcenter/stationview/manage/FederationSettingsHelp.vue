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
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.federationSettings.title')" :subtitle="t('helpCenter.federationSettings.subtitle')">
    <HelpSection :title="t('helpCenter.federationSettings.visibilityTitle')">
      <p>{{ t('helpCenter.federationSettings.visibilityText') }}</p>
    </HelpSection>

    <!-- Dummy: Discovery visibility -->
    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('discovery.settings.visibility') }}</SubHeader>
      <SelectInput model-value="INSTANCE" disabled class="w-full">
        <option value="NONE">{{ t('discovery.settings.visibilityNone') }}</option>
        <option value="INSTANCE">{{ t('discovery.settings.visibilityInstance') }}</option>
        <option value="PUBLIC">{{ t('discovery.settings.visibilityPublic') }}</option>
      </SelectInput>

      <div class="space-y-1">
        <FieldLabel>{{ t('discovery.settings.description') }}</FieldLabel>
        <TextInput model-value="Jugendfeuerwehr Musterstadt" :placeholder="t('discovery.settings.descriptionPlaceholder')" disabled/>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.federationSettings.publicKbTitle')">
      <p>{{ t('helpCenter.federationSettings.publicKbText') }}</p>
    </HelpSection>

    <!-- Dummy: Public KB toggle -->
    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('stationManage.publicKb.title') }}</SubHeader>
      <MutedText size="sm">{{ t('stationManage.publicKb.hint') }}</MutedText>
      <div class="flex items-center justify-between">
        <span class="text-sm font-medium">{{ t('stationManage.publicKb.enabled') }}</span>
        <ToggleInput :model-value="true" disabled/>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.federationSettings.publicCalendarTitle')">
      <p>{{ t('helpCenter.federationSettings.publicCalendarText') }}</p>
    </HelpSection>

    <!-- Dummy: Public calendar toggle -->
    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('stationManage.publicCalendar.title') }}</SubHeader>
      <MutedText size="sm">{{ t('stationManage.publicCalendar.hint') }}</MutedText>
      <div class="flex items-center justify-between">
        <span class="text-sm font-medium">{{ t('stationManage.publicCalendar.enabled') }}</span>
        <ToggleInput :model-value="false" disabled/>
      </div>
    </NeutralContainer>

    <!-- Dummy: Save button -->
    <NeutralContainer>
      <PrimaryButton disabled>{{ t('stationManage.save') }}</PrimaryButton>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.federationSettings.tip') }}</HelpTip>
  </HelpArticle>
</template>
