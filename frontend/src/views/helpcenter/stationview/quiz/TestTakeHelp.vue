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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quizTestTake.title')" :subtitle="t('helpCenter.quizTestTake.subtitle')">
    <HelpSection :title="t('helpCenter.quizTestTake.whatIs')">
      <p>{{ t('helpCenter.quizTestTake.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizTestTake.howTo')">
      <p>{{ t('helpCenter.quizTestTake.step1') }}</p>
      <p>{{ t('helpCenter.quizTestTake.step2') }}</p>
      <p>{{ t('helpCenter.quizTestTake.step3') }}</p>
    </HelpSection>

    <!-- Dummy: Timer and progress -->
    <HelpSection :title="t('helpCenter.quizTestTake.progressTitle')">
      <p>{{ t('helpCenter.quizTestTake.progressText') }}</p>
      <NeutralContainer class="space-y-3">
        <!-- Timer and question counter -->
        <div class="flex items-center justify-between">
          <span class="text-sm text-(--text-muted)">{{ t('helpCenter.quizTestTake.questionCounter', { current: 3, total: 15 }) }}</span>
          <span class="text-sm font-mono font-bold">
            <font-awesome-icon :icon="['fas', 'clock']" class="mr-1" />
            24:31
          </span>
        </div>

        <!-- Progress dots -->
        <div class="flex flex-wrap gap-1">
          <IconButton v-for="i in 15" :key="i"
            :icon="['fas', 'circle']"
            :label="String(i)"
            class="w-7 h-7 rounded-full text-xs font-medium"
            :class="{
              'bg-primary text-primary-text': i === 3,
              'bg-primary/20 text-primary': i <= 2,
              'bg-bg-light-accent dark:bg-bg-dark-accent text-(--text-muted)': i > 3,
            }"
            disabled
          >
            {{ i }}
          </IconButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Dummy: Question view -->
    <HelpSection :title="t('helpCenter.quizTestTake.questionTitle')">
      <p>{{ t('helpCenter.quizTestTake.questionText') }}</p>
      <NeutralContainer class="space-y-3">
        <p class="font-medium">{{ t('helpCenter.quizTestTake.exampleQuestion') }}</p>
        <div class="space-y-2 text-sm">
          <FieldLabel inline class="cursor-not-allowed">
            <ToggleInput :model-value="false" disabled /> 110
          </FieldLabel>
          <FieldLabel inline class="cursor-not-allowed">
            <ToggleInput :model-value="true" disabled /> 112
          </FieldLabel>
          <FieldLabel inline class="cursor-not-allowed">
            <ToggleInput :model-value="false" disabled /> 118
          </FieldLabel>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Question types -->
    <HelpSection :title="t('helpCenter.quizTestTake.typesTitle')">
      <p>{{ t('helpCenter.quizTestTake.typesText') }}</p>
      <p>{{ t('helpCenter.quizTestTake.typesList') }}</p>
    </HelpSection>

    <!-- Dummy: Navigation buttons -->
    <HelpSection :title="t('helpCenter.quizTestTake.navigationTitle')">
      <p>{{ t('helpCenter.quizTestTake.navigationText') }}</p>
      <div class="flex items-center justify-between">
        <SecondaryButton :icon="['fas', 'chevron-left']" disabled>
          {{ t('quiz.attempt.prev') }}
        </SecondaryButton>
        <SuccessButton :icon="['fas', 'paper-plane']" disabled>
          {{ t('quiz.attempt.submit') }}
        </SuccessButton>
        <SecondaryButton disabled>
          {{ t('quiz.attempt.next') }}
          <font-awesome-icon :icon="['fas', 'chevron-right']" class="ml-1" />
        </SecondaryButton>
      </div>
    </HelpSection>

    <!-- Dummy: Submit confirmation modal -->
    <HelpSection :title="t('helpCenter.quizTestTake.submitTitle')">
      <p>{{ t('helpCenter.quizTestTake.submitText') }}</p>
      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('quiz.attempt.confirmSubmitTitle') }}</SectionHeader>
        <p class="text-sm">{{ t('quiz.attempt.confirmSubmitMessage') }}</p>
        <div class="flex justify-end gap-3">
          <SecondaryButton disabled>{{ t('common.cancel') }}</SecondaryButton>
          <SuccessButton disabled>{{ t('quiz.attempt.submit') }}</SuccessButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quizTestTake.tip') }}</HelpTip>
  </HelpArticle>
</template>
