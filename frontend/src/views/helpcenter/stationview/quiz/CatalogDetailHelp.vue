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
import InfoContainer from '@/components/container/InfoContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SectionLabel from '@/components/typography/SectionLabel.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quiz.catalogDetailTitle')" :subtitle="t('helpCenter.quiz.catalogDetailSubtitle')">
    <HelpSection :title="t('helpCenter.quiz.addQuestionsTitle')">
      <p>{{ t('helpCenter.quiz.addQuestionsText') }}</p>
    </HelpSection>

    <!-- Dummy: Add question buttons -->
    <div class="flex flex-wrap gap-2">
      <SecondaryButton :icon="['fas', 'plus']" disabled>
        {{ t('quiz.questionTypes.MULTIPLE_CHOICE') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'plus']" disabled>
        {{ t('quiz.questionTypes.TRUE_FALSE') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'plus']" disabled>
        {{ t('quiz.questionTypes.FILL_IN_THE_BLANK') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'plus']" disabled>
        {{ t('quiz.questionTypes.FREE_ANSWER') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'plus']" disabled>
        {{ t('quiz.questionTypes.ORDERING') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'plus']" disabled>
        {{ t('quiz.questionTypes.CONNECT') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'plus']" disabled>
        {{ t('quiz.questionTypes.IMAGE_TEXT') }}
      </SecondaryButton>
    </div>

    <HelpSection :title="t('helpCenter.quiz.questionTypesTitle')">
      <p>{{ t('helpCenter.quiz.questionTypesIntro') }}</p>
    </HelpSection>

    <HelpSection :title="t('quiz.questionTypes.MULTIPLE_CHOICE')">
      <p>{{ t('helpCenter.quiz.typeMultipleChoiceText') }}</p>
    </HelpSection>

    <!-- Dummy: Multiple choice question -->
    <NeutralContainer>
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <SectionLabel>1. {{ t('quiz.questionTypes.MULTIPLE_CHOICE') }}</SectionLabel>
          <DeleteButton disabled />
        </div>
        <TextInput model-value="Was ist kein Löschmittel?" disabled />
        <div class="space-y-1 opacity-60 text-sm">
          <label class="flex items-center gap-2"><ToggleInput :model-value="false" disabled /> Wasser</label>
          <label class="flex items-center gap-2"><ToggleInput :model-value="true" disabled /> Benzin</label>
          <label class="flex items-center gap-2"><ToggleInput :model-value="false" disabled /> Schaum</label>
          <label class="flex items-center gap-2"><ToggleInput :model-value="false" disabled /> CO2</label>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('quiz.questionTypes.TRUE_FALSE')">
      <p>{{ t('helpCenter.quiz.typeTrueFalseText') }}</p>
    </HelpSection>

    <HelpSection :title="t('quiz.questionTypes.FILL_IN_THE_BLANK')">
      <p>{{ t('helpCenter.quiz.typeFillBlankText') }}</p>
    </HelpSection>

    <HelpSection :title="t('quiz.questionTypes.FREE_ANSWER')">
      <p>{{ t('helpCenter.quiz.typeFreeAnswerText') }}</p>
    </HelpSection>

    <HelpSection :title="t('quiz.questionTypes.ORDERING')">
      <p>{{ t('helpCenter.quiz.typeOrderingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('quiz.questionTypes.CONNECT')">
      <p>{{ t('helpCenter.quiz.typeConnectText') }}</p>
    </HelpSection>

    <HelpSection :title="t('quiz.questionTypes.IMAGE_TEXT')">
      <p>{{ t('helpCenter.quiz.typeImageTextText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.categoriesTitle')">
      <p>{{ t('helpCenter.quiz.categoriesText') }}</p>
    </HelpSection>

    <!-- Dummy: Categories -->
    <NeutralContainer>
      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <span class="font-medium text-sm">Grundwissen</span>
          <span class="text-xs text-(--text-muted)">5 {{ t('quiz.questions.title') }}</span>
        </div>
        <div class="flex items-center justify-between">
          <span class="font-medium text-sm">Fortgeschritten</span>
          <span class="text-xs text-(--text-muted)">7 {{ t('quiz.questions.title') }}</span>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.quiz.reportsTitle')">
      <p>{{ t('helpCenter.quiz.reportsText') }}</p>
      <p>{{ t('helpCenter.quiz.reportsAcknowledge') }}</p>
    </HelpSection>

    <!-- Dummy: an open note on a question -->
    <InfoContainer class="space-y-2">
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', 'flag']" />
        <span class="text-sm font-medium">{{ t('quiz.report.openCount', {count: 1}) }}</span>
      </div>
      <div class="flex items-start justify-between gap-3">
        <div>
          <p class="text-sm">Die Antwort stimmt so nicht mehr, seit 2024 gilt eine neue Regel.</p>
          <MutedText class="block text-xs">Nora &bull; 12.03.2026 18:40</MutedText>
        </div>
        <SecondaryButton class="shrink-0 text-xs" :icon="['fas', 'check']" disabled>
          {{ t('quiz.report.acknowledge') }}
        </SecondaryButton>
      </div>
    </InfoContainer>

    <HelpTip>{{ t('helpCenter.quiz.catalogDetailTip') }}</HelpTip>
  </HelpArticle>
</template>
