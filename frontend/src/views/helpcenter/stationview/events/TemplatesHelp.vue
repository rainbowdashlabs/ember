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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventTemplates.title')" :subtitle="t('helpCenter.eventTemplates.subtitle')">
    <HelpSection :title="t('helpCenter.eventTemplates.whatIs')">
      <p>{{ t('helpCenter.eventTemplates.whatIsText') }}</p>
    </HelpSection>

    <!-- Dummy: Template list -->
    <HelpSection :title="t('helpCenter.eventTemplates.listTitle')">
      <p>{{ t('helpCenter.eventTemplates.listText') }}</p>
      <div class="flex items-center justify-between mb-3 mt-3">
        <SectionHeader>{{ t('eventTemplates.title') }}</SectionHeader>
        <PrimaryButton :icon="['fas', 'plus']">{{ t('eventTemplates.create') }}</PrimaryButton>
      </div>
      <div class="space-y-2">
        <NeutralContainer class="flex items-center justify-between">
          <div>
            <span class="font-medium">Standard-Übung</span>
            <MutedText size="sm" class="ml-2">Übungsabend</MutedText>
          </div>
          <div class="flex items-center gap-2">
            <EditButton/>
            <DeleteButton/>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between">
          <div>
            <span class="font-medium">Wettkampf</span>
            <MutedText size="sm" class="ml-2">Wettkampf Vorbereitung</MutedText>
          </div>
          <div class="flex items-center gap-2">
            <EditButton/>
            <DeleteButton/>
          </div>
        </NeutralContainer>
      </div>
    </HelpSection>

    <!-- Dummy: Template edit form -->
    <HelpSection :title="t('helpCenter.eventTemplates.editTitle')">
      <p>{{ t('helpCenter.eventTemplates.editText') }}</p>
      <NeutralContainer class="space-y-4 mt-3">
        <div class="flex items-center gap-2">
          <SecondaryButton :icon="['fas', 'arrow-left']"/>
          <SectionHeader>{{ t('eventTemplates.edit') }}</SectionHeader>
        </div>
        <SubHeader>{{ t('eventTemplates.name') }}</SubHeader>
        <TextInput model-value="Standard-Übung" disabled/>

        <SubHeader>{{ t('eventTemplates.eventDefaults') }}</SubHeader>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="space-y-1">
            <FieldLabel>{{ t('eventTemplates.eventTitle') }}</FieldLabel>
            <TextInput model-value="Übungsabend" disabled/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('eventTemplates.category') }}</FieldLabel>
            <SelectInput model-value="1" disabled class="w-full">
              <option value="1">Übung</option>
            </SelectInput>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('eventTemplates.eventType') }}</FieldLabel>
            <SelectInput model-value="RECURRING" disabled class="w-full">
              <option value="RECURRING">{{ t('events.typeRecurring') }}</option>
            </SelectInput>
          </div>
        </div>
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium">{{ t('events.requiresRegistration') }}</span>
          <ToggleInput :model-value="false"/>
        </div>
        <div class="flex items-center justify-between">
          <span class="text-sm font-medium">{{ t('events.requiresConfirmation') }}</span>
          <ToggleInput :model-value="false"/>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplates.fieldsTitle')">
      <p>{{ t('helpCenter.eventTemplates.fieldsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplates.applyTitle')">
      <p>{{ t('helpCenter.eventTemplates.applyText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.eventTemplates.tip') }}</HelpTip>
  </HelpArticle>
</template>
