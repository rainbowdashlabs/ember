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
  <HelpArticle :title="t('helpCenter.courseEdit.title')" :subtitle="t('helpCenter.courseEdit.subtitle')">
    <HelpSection :title="t('helpCenter.courseEdit.whatIs')">
      <p>{{ t('helpCenter.courseEdit.whatIsText') }}</p>
      <NeutralContainer class="space-y-2">
        <FieldLabel>{{ t('courses.name') }}</FieldLabel>
        <TextInput :model-value="t('helpCenter.courses.exampleName')"/>
        <FieldLabel>{{ t('courses.limit') }}</FieldLabel>
        <NumberInput :model-value="12"/>
        <FieldLabel inline><ToggleInput :model-value="true"/> {{ t('courses.confirmationField') }}</FieldLabel>
      </NeutralContainer>
    </HelpSection>
    <HelpSection :title="t('helpCenter.courseEdit.setsTitle')">
      <p>{{ t('helpCenter.courseEdit.setsText') }}</p>
      <NeutralContainer class="space-y-2">
        <div class="flex items-center gap-2">
          <TextInput :model-value="t('helpCenter.courseDetail.practice')" class="flex-1"/>
          <NumberInput :model-value="2" class="w-24"/>
          <DeleteButton :label="t('courses.removeSet')"/>
        </div>
        <div class="flex items-center gap-2">
          <TextInput :model-value="t('helpCenter.courseDetail.test')" class="flex-1"/>
          <NumberInput :model-value="1" class="w-24"/>
          <DeleteButton :label="t('courses.removeSet')"/>
        </div>
        <SecondaryButton :icon="['fas', 'plus']">{{ t('courses.addSet') }}</SecondaryButton>
      </NeutralContainer>
    </HelpSection>
    <HelpSection :title="t('helpCenter.courseEdit.audienceTitle')">
      <p>{{ t('helpCenter.courseEdit.audienceText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.courseEdit.questionsTitle')">
      <p>{{ t('helpCenter.courseEdit.questionsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.courseEdit.partsTitle')">
      <p>{{ t('helpCenter.courseEdit.partsText') }}</p>
      <NeutralContainer class="flex flex-wrap items-center justify-between gap-2">
        <span class="font-medium">{{ t('helpCenter.courseDetail.day1') }}</span>
        <div class="flex items-center gap-2">
          <SelectInput :model-value="1"><option :value="1">{{ t('helpCenter.courseDetail.practice') }}</option></SelectInput>
          <IconButton :icon="['fas', 'link-slash']" :label="t('courses.removePart')"/>
        </div>
      </NeutralContainer>
      <ButtonRow>
        <PrimaryButton :icon="['fas', 'plus']">{{ t('courses.addExisting') }}</PrimaryButton>
        <PrimaryButton :icon="['fas', 'plus']">{{ t('courses.createPart') }}</PrimaryButton>
      </ButtonRow>
      <p>{{ t('helpCenter.courseEdit.partRulesText') }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.courseEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
