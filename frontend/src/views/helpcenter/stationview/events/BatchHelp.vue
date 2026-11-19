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
import StepProgressBar from '@/components/display/StepProgressBar.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()

const modes = ['batchCreate.schedule', 'batchCreate.csvImport', 'batchCreate.manualEntry'] as const

const exampleRows = [
  {date: '2026-12-04', start: '16:30', end: '19:00', name: 'helpCenter.batchCreate.manualExampleName'},
  {date: '2026-12-04', start: '19:00', end: '21:30', name: 'helpCenter.batchCreate.manualExampleNameLate'},
] as const
</script>

<template>
  <HelpArticle :title="t('helpCenter.batchCreate.title')" :subtitle="t('helpCenter.batchCreate.subtitle')">
    <HelpSection :title="t('helpCenter.batchCreate.whatIs')">
      <p>{{ t('helpCenter.batchCreate.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.batchCreate.steps')">
      <p>{{ t('helpCenter.batchCreate.stepsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('batchCreate.title')">
      <SectionHeader>{{ t('batchCreate.title') }}</SectionHeader>
      <StepProgressBar
          class="mb-4"
          :steps="[
            { label: t('batchCreate.stepFields') },
            { label: t('batchCreate.stepSchedule'), disabled: true },
            { label: t('batchCreate.stepTable'), disabled: true },
          ]"
          :current-step="0"
      />

      <div class="space-y-3">
        <NeutralContainer class="space-y-2">
          <span class="font-medium text-primary">{{ t('helpCenter.batchCreate.step1Title') }}</span>
          <p class="text-sm">{{ t('helpCenter.batchCreate.step1Text') }}</p>
        </NeutralContainer>
        <NeutralContainer class="space-y-2">
          <span class="font-medium text-primary">{{ t('helpCenter.batchCreate.step2Title') }}</span>
          <p class="text-sm">{{ t('helpCenter.batchCreate.step2Text') }}</p>
        </NeutralContainer>
        <NeutralContainer class="space-y-2">
          <span class="font-medium text-primary">{{ t('helpCenter.batchCreate.step3Title') }}</span>
          <p class="text-sm">{{ t('helpCenter.batchCreate.step3Text') }}</p>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.batchCreate.waysTitle')">
      <p>{{ t('helpCenter.batchCreate.waysSchedule') }}</p>
      <p>{{ t('helpCenter.batchCreate.waysCsv') }}</p>
      <p>{{ t('helpCenter.batchCreate.waysManual') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.batchCreate.templateTitle')">
      <p>{{ t('helpCenter.batchCreate.templateText') }}</p>
      <p>{{ t('helpCenter.batchCreate.templateLeftOutText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.batchCreate.manualExampleTitle')">
      <NeutralContainer class="space-y-4">
        <div class="flex flex-wrap gap-2">
          <SelectionToggleButton v-for="mode in modes" :key="mode" :selected="mode === 'batchCreate.manualEntry'" size="md">
            {{ t(mode) }}
          </SelectionToggleButton>
        </div>
        <MutedText tag="p" size="sm">{{ t('batchCreate.manual.hint') }}</MutedText>
        <ul class="space-y-3">
          <li
              v-for="row in exampleRows"
              :key="row.name"
              class="grid gap-3 rounded-theme border border-(--border) p-3 sm:grid-cols-[1fr_auto_auto_2fr_auto] sm:items-start"
          >
            <LabelledField :label="t('batchCreate.date')">
              <DateInput :model-value="row.date" disabled/>
            </LabelledField>
            <LabelledField :label="t('batchCreate.startTime')">
              <TimeShortInput :model-value="row.start" disabled/>
            </LabelledField>
            <LabelledField :label="t('batchCreate.endTime')">
              <TimeShortInput :model-value="row.end" disabled/>
            </LabelledField>
            <LabelledField :label="t('batchCreate.eventName')" hint>
              <TextInput :model-value="t(row.name)" disabled/>
            </LabelledField>
            <div class="flex justify-end sm:pt-6">
              <DeleteButton disabled/>
            </div>
          </li>
        </ul>
        <ButtonRow align="between">
          <SecondaryButton :icon="['fas', 'plus']">{{ t('batchCreate.manual.addRow') }}</SecondaryButton>
          <PrimaryButton>{{ t('common.next') }}</PrimaryButton>
        </ButtonRow>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.batchCreate.manualTip') }}</HelpTip>
    <HelpTip>{{ t('helpCenter.batchCreate.tip') }}</HelpTip>
  </HelpArticle>
</template>
