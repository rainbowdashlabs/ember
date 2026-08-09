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
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()

const TEMPLATES = [
  {name: 'Standard-Übung', title: 'Übungsabend'},
  {name: 'Wettkampf', title: 'Wettkampf Vorbereitung'},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventTemplates.title')" :subtitle="t('helpCenter.eventTemplates.subtitle')">
    <HelpSection :title="t('helpCenter.eventTemplates.whatIs')">
      <p>{{ t('helpCenter.eventTemplates.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplates.howTo')">
      <p>{{ t('helpCenter.eventTemplates.howToStep1') }}</p>
      <p>{{ t('helpCenter.eventTemplates.howToStep2') }}</p>
      <p>{{ t('helpCenter.eventTemplates.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplates.listTitle')">
      <p>{{ t('helpCenter.eventTemplates.listText') }}</p>
      <div class="flex items-center justify-end mb-3 mt-3">
        <PrimaryButton :icon="['fas', 'plus']">{{ t('eventTemplates.create') }}</PrimaryButton>
      </div>
      <div class="space-y-2">
        <NeutralContainer v-for="tpl in TEMPLATES" :key="tpl.name" class="flex items-center justify-between">
          <div>
            <span class="font-medium">{{ tpl.name }}</span>
            <MutedText size="sm" class="ml-2">{{ tpl.title }}</MutedText>
          </div>
          <div class="flex items-center gap-2">
            <EditButton/>
            <DeleteButton/>
          </div>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplates.createTitle')">
      <p>{{ t('helpCenter.eventTemplates.createText') }}</p>
      <NeutralContainer class="space-y-4 mt-3">
        <SubHeader>{{ t('eventTemplates.create') }}</SubHeader>
        <FieldLabel>{{ t('eventTemplates.name') }}</FieldLabel>
        <TextInput model-value="" :placeholder="t('eventTemplates.namePlaceholder')"/>
        <div class="flex justify-end">
          <PrimaryButton disabled>{{ t('eventTemplates.create') }}</PrimaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventTemplates.applyTitle')">
      <p>{{ t('helpCenter.eventTemplates.applyText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.eventTemplates.tip') }}</HelpTip>
  </HelpArticle>
</template>
