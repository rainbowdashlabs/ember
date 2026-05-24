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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventEdit.title')" :subtitle="t('helpCenter.eventEdit.subtitle')">
    <HelpSection :title="t('helpCenter.eventEdit.whatShown')">
      <p>{{ t('helpCenter.eventEdit.whatShownText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('events.general') }}</SectionHeader>
      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1">
          <FieldLabel>{{ t('events.name') }}</FieldLabel>
          <TextInput model-value="Übungsabend"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('events.type') }}</FieldLabel>
          <TextInput model-value="Wiederkehrend" disabled/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('events.startTime') }}</FieldLabel>
          <TimeShortInput model-value="18:00"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('events.endTime') }}</FieldLabel>
          <TimeShortInput model-value="20:00"/>
        </div>
      </div>
    </NeutralContainer>

    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('events.registration') }}</SectionHeader>
      <div class="flex items-center gap-3">
        <ToggleInput :model-value="true"/>
        <span class="text-sm">{{ t('events.requiresRegistration') }}</span>
      </div>
      <div class="flex items-center gap-3">
        <ToggleInput :model-value="false"/>
        <span class="text-sm">{{ t('events.requiresConfirmation') }}</span>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.eventEdit.generalTitle')">
      <p>{{ t('helpCenter.eventEdit.generalText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventEdit.templateTitle')">
      <p>{{ t('helpCenter.eventEdit.templateText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventEdit.registrationTitle')">
      <p>{{ t('helpCenter.eventEdit.registrationText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventEdit.restrictionsTitle')">
      <p>{{ t('helpCenter.eventEdit.restrictionsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventEdit.fieldDefaultsTitle')">
      <p>{{ t('helpCenter.eventEdit.fieldDefaultsText') }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.eventEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
