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
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quizTestEdit.title')" :subtitle="t('helpCenter.quizTestEdit.subtitle')">
    <HelpSection :title="t('helpCenter.quizTestEdit.whatIs')">
      <p>{{ t('helpCenter.quizTestEdit.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizTestEdit.basicTitle')">
      <p>{{ t('helpCenter.quizTestEdit.basicText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-3">
      <TextInput model-value="Jugendflamme Stufe 1 - Theorieteil" disabled :placeholder="t('helpCenter.quizTestEdit.nameLabel')" />
      <div class="flex items-center gap-2 text-xs">
        <ToggleInput :model-value="true" disabled />
        <span>{{ t('helpCenter.quizTestEdit.shuffleLabel') }}</span>
      </div>
      <div class="flex items-center gap-2 text-xs">
        <ToggleInput :model-value="false" disabled />
        <span>{{ t('helpCenter.quizTestEdit.timeLimitLabel') }}</span>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.quizTestEdit.sectionsTitle')">
      <p>{{ t('helpCenter.quizTestEdit.sectionsText') }}</p>
    </HelpSection>

    <NeutralContainer>
      <div class="space-y-2 text-sm">
        <div class="font-semibold">{{ t('helpCenter.sample.quiz.section', {number: 1}) }}</div>
        <div class="flex gap-4 text-xs text-[var(--text-muted)]">
          <span>{{ t('helpCenter.sample.quiz.catalog', {name: t('helpCenter.sample.quiz.basicKnowledge')}) }}</span>
          <span>{{ t('helpCenter.sample.quiz.questionCount', {count: 10}) }}</span>
          <span>{{ t('helpCenter.sample.quiz.random') }}</span>
        </div>
        <div class="font-semibold mt-2">{{ t('helpCenter.sample.quiz.section', {number: 2}) }}</div>
        <div class="flex gap-4 text-xs text-[var(--text-muted)]">
          <span>{{ t('helpCenter.sample.quiz.catalog', {name: t('helpCenter.sample.quiz.equipment')}) }}</span>
          <span>{{ t('helpCenter.sample.quiz.questionCount', {count: 5}) }}</span>
          <span>{{ t('helpCenter.sample.quiz.random') }}</span>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.quizTestEdit.restrictionsTitle')">
      <p>{{ t('helpCenter.quizTestEdit.restrictionsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quizTestEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
