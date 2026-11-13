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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventGroupEdit.title')" :subtitle="t('helpCenter.eventGroupEdit.subtitle')">
    <HelpSection :title="t('helpCenter.eventGroupEdit.whatIs')">
      <p>{{ t('helpCenter.eventGroupEdit.whatIsText') }}</p>
      <NeutralContainer class="space-y-2">
        <FieldLabel>{{ t('eventGroups.name') }}</FieldLabel>
        <TextInput :model-value="t('helpCenter.eventGroups.exampleName')"/>
        <FieldLabel>{{ t('eventGroups.limit') }}</FieldLabel>
        <NumberInput :model-value="12"/>
        <FieldLabel inline><ToggleInput :model-value="true"/> {{ t('eventGroups.confirmationField') }}</FieldLabel>
      </NeutralContainer>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventGroupEdit.setsTitle')">
      <p>{{ t('helpCenter.eventGroupEdit.setsText') }}</p>
      <NeutralContainer class="space-y-2">
        <div class="flex items-center gap-2">
          <TextInput :model-value="t('helpCenter.eventGroupDetail.practice')" class="flex-1"/>
          <NumberInput :model-value="2" class="w-24"/>
          <DeleteButton :label="t('eventGroups.removeSet')"/>
        </div>
        <div class="flex items-center gap-2">
          <TextInput :model-value="t('helpCenter.eventGroupDetail.test')" class="flex-1"/>
          <NumberInput :model-value="1" class="w-24"/>
          <DeleteButton :label="t('eventGroups.removeSet')"/>
        </div>
        <SecondaryButton :icon="['fas', 'plus']">{{ t('eventGroups.addSet') }}</SecondaryButton>
      </NeutralContainer>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventGroupEdit.audienceTitle')">
      <p>{{ t('helpCenter.eventGroupEdit.audienceText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventGroupEdit.fieldsTitle')">
      <p>{{ t('helpCenter.eventGroupEdit.fieldsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventGroupEdit.questionsTitle')">
      <p>{{ t('helpCenter.eventGroupEdit.questionsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventGroupEdit.partsTitle')">
      <p>{{ t('helpCenter.eventGroupEdit.partsText') }}</p>
      <NeutralContainer class="flex flex-wrap items-center justify-between gap-2">
        <span class="font-medium">{{ t('helpCenter.eventGroupDetail.day1') }}</span>
        <div class="flex items-center gap-2">
          <SelectInput :model-value="1"><option :value="1">{{ t('helpCenter.eventGroupDetail.practice') }}</option></SelectInput>
          <IconButton :icon="['fas', 'link-slash']" :label="t('eventGroups.removePart')"/>
        </div>
      </NeutralContainer>
      <ButtonRow>
        <PrimaryButton :icon="['fas', 'plus']">{{ t('eventGroups.addExisting') }}</PrimaryButton>
        <PrimaryButton :icon="['fas', 'plus']">{{ t('eventGroups.createPart') }}</PrimaryButton>
      </ButtonRow>
      <p>{{ t('helpCenter.eventGroupEdit.partRulesText') }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.eventGroupEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
