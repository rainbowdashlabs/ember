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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventLayouts.title')" :subtitle="t('helpCenter.eventLayouts.subtitle')">
    <HelpSection :title="t('helpCenter.eventLayouts.whatIs')">
      <p>{{ t('helpCenter.eventLayouts.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventLayouts.howTo')">
      <p>{{ t('helpCenter.eventLayouts.howToText') }}</p>
    </HelpSection>

    <!-- Dummy: Layout list -->
    <HelpSection :title="t('eventLayouts.title')">
      <div class="flex items-center justify-between mb-3">
        <SectionHeader>{{ t('eventLayouts.title') }}</SectionHeader>
        <PrimaryButton :icon="['fas', 'plus']">
          {{ t('eventLayouts.createLayout') }}
        </PrimaryButton>
      </div>

      <div class="space-y-2">
        <NeutralContainer class="flex items-center justify-between">
          <span class="font-medium">Standard-Übung</span>
          <div class="flex gap-2">
            <EditButton :label="t('common.edit')"/>
            <DeleteButton :label="t('common.delete')"/>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between">
          <span class="font-medium">Ausflug</span>
          <div class="flex gap-2">
            <EditButton :label="t('common.edit')"/>
            <DeleteButton :label="t('common.delete')"/>
          </div>
        </NeutralContainer>
      </div>
    </HelpSection>

    <!-- Dummy: Edit modal snapshot -->
    <HelpSection :title="t('helpCenter.eventLayouts.editTitle')">
      <p>{{ t('helpCenter.eventLayouts.editText') }}</p>
      <NeutralContainer class="space-y-4 mt-3">
        <SectionHeader>{{ t('eventLayouts.editLayout') }}</SectionHeader>
        <TextInput model-value="Standard-Übung" disabled/>

        <div class="flex items-center justify-between">
          <SubHeader>{{ t('eventLayouts.fields') }}</SubHeader>
          <SecondaryButton :icon="['fas', 'plus']">
            {{ t('eventFields.addField') }}
          </SecondaryButton>
        </div>

        <NeutralContainer class="space-y-2">
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium">Treffpunkt</span>
            <MutedText size="sm">{{ t('eventFields.typeString') }}</MutedText>
          </div>
        </NeutralContainer>
        <NeutralContainer class="space-y-2">
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium">Hinweis</span>
            <MutedText size="sm">{{ t('eventFields.typeTextarea') }}</MutedText>
          </div>
        </NeutralContainer>

        <div class="flex justify-end gap-3">
          <SecondaryButton>{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton>{{ t('common.save') }}</PrimaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.eventLayouts.tip') }}</HelpTip>
  </HelpArticle>
</template>
