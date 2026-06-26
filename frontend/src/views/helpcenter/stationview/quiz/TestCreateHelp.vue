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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import DummySectionsSystem from '@/views/helpcenter/stationview/quiz/testcreatehelp/DummySectionsSystem.vue'

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

    <DummySectionsSystem />

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
