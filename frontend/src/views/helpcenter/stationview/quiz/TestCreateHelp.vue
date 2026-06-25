/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SectionLabel from '@/components/typography/SectionLabel.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quizTestCreate.title')" :subtitle="t('helpCenter.quizTestCreate.subtitle')">
    <HelpSection :title="t('helpCenter.quizTestCreate.whatIs')">
      <p>{{ t('helpCenter.quizTestCreate.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizTestCreate.howTo')">
      <p>{{ t('helpCenter.quizTestCreate.step1') }}</p>
      <p>{{ t('helpCenter.quizTestCreate.step2') }}</p>
      <p>{{ t('helpCenter.quizTestCreate.step3') }}</p>
    </HelpSection>

    <!-- Dummy: Test metadata -->
    <HelpSection :title="t('helpCenter.quizTestCreate.basicTitle')">
      <p>{{ t('helpCenter.quizTestCreate.basicText') }}</p>
      <NeutralContainer class="space-y-4">
        <TextInput :model-value="'Jugendflamme Stufe 1 — Wissenstest'" disabled />
        <TextAreaInput :model-value="'Theoretischer Teil der Prüfung'" disabled />

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <FieldLabel hint class="mb-1">{{ t('quiz.tests.startAt') }}</FieldLabel>
            <DateTimeInput :model-value="'2025-06-15T09:00'" disabled />
          </div>
          <div>
            <FieldLabel hint class="mb-1">{{ t('quiz.tests.endAt') }}</FieldLabel>
            <DateTimeInput :model-value="'2025-06-15T12:00'" disabled />
          </div>
        </div>

        <div class="flex flex-wrap gap-6">
          <FieldLabel inline>
            <ToggleInput :model-value="true" disabled />
            {{ t('quiz.tests.shuffle') }}
          </FieldLabel>
          <FieldLabel inline>
            <ToggleInput :model-value="true" disabled />
            {{ t('quiz.tests.timeLimitEnabled') }}
          </FieldLabel>
        </div>

        <div class="flex items-center gap-2">
          <label class="text-sm text-(--text-muted)">{{ t('quiz.tests.timeLimitMinutes') }}</label>
          <NumberInput :model-value="30" class="w-24" disabled />
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Dummy: Restrictions -->
    <HelpSection :title="t('helpCenter.quizTestCreate.restrictionsTitle')">
      <p>{{ t('helpCenter.quizTestCreate.restrictionsText') }}</p>
      <NeutralContainer class="space-y-3">
        <SubHeader>{{ t('quiz.tests.restrictions') }}</SubHeader>
        <p class="text-xs text-(--text-muted)">{{ t('helpCenter.quizTestCreate.restrictionsHint') }}</p>
      </NeutralContainer>
    </HelpSection>

    <!-- Dummy: Sections system -->
    <HelpSection :title="t('helpCenter.quizTestCreate.sectionsTitle')">
      <p>{{ t('helpCenter.quizTestCreate.sectionsText') }}</p>

      <div class="space-y-4">
        <div class="flex items-center justify-between">
          <SectionHeader>{{ t('quiz.sections.title') }}</SectionHeader>
          <SecondaryButton :icon="['fas', 'plus']" disabled>
            {{ t('quiz.sections.add') }}
          </SecondaryButton>
        </div>

        <NeutralContainer>
          <div class="space-y-4">
            <div class="flex items-center justify-between">
              <SectionLabel>{{ t('helpCenter.quizTestCreate.sectionLabel', { n: 1 }) }}</SectionLabel>
              <div class="flex gap-1">
                <DeleteButton disabled />
              </div>
            </div>

            <TextInput :model-value="'Grundwissen Brandschutz'" disabled />
            <TextAreaInput :model-value="'Fragen zu den Grundlagen'" disabled />

            <div class="space-y-3">
              <label class="text-xs text-(--text-muted) font-medium">{{ t('quiz.sections.sources') }}</label>

              <div class="flex flex-col sm:flex-row gap-2 items-start sm:items-center p-3 rounded border border-bg-light-accent dark:border-bg-dark-accent">
                <SelectInput :model-value="'1'" class="flex-1" disabled>
                  <option value="1">Brandschutz-Katalog</option>
                </SelectInput>

                <SelectInput :model-value="''" class="flex-1" disabled>
                  <option value="">{{ t('quiz.sections.allCategories') }}</option>
                </SelectInput>

                <div class="flex items-center gap-2">
                  <label class="text-xs text-(--text-muted) whitespace-nowrap">{{ t('quiz.sections.questionCount') }}</label>
                  <NumberInput :model-value="10" class="w-20" disabled />
                </div>

                <DeleteButton disabled />
              </div>

              <SecondaryButton :icon="['fas', 'plus']" disabled>
                {{ t('quiz.sections.addSource') }}
              </SecondaryButton>
            </div>
          </div>
        </NeutralContainer>
      </div>
    </HelpSection>

    <!-- Actions -->
    <HelpSection :title="t('helpCenter.quizTestCreate.nextTitle')">
      <p>{{ t('helpCenter.quizTestCreate.nextText') }}</p>
      <div class="flex justify-end gap-3">
        <SecondaryButton disabled>{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton disabled>{{ t('common.save') }}</PrimaryButton>
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quizTestCreate.tip') }}</HelpTip>
  </HelpArticle>
</template>
