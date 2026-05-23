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
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quiz.catalogDetailTitle')" :subtitle="t('helpCenter.quiz.catalogDetailSubtitle')">
    <HelpSection :title="t('helpCenter.quiz.addQuestionsTitle')">
      <p>{{ t('helpCenter.quiz.addQuestionsText') }}</p>
    </HelpSection>

    <!-- Dummy: Add question buttons -->
    <div class="flex flex-wrap gap-2">
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('quiz.questionTypes.MULTIPLE_CHOICE') }}
      </SecondaryButton>
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('quiz.questionTypes.TRUE_FALSE') }}
      </SecondaryButton>
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('quiz.questionTypes.FILL_IN_THE_BLANK') }}
      </SecondaryButton>
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('quiz.questionTypes.FREE_ANSWER') }}
      </SecondaryButton>
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('quiz.questionTypes.ORDERING') }}
      </SecondaryButton>
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
        {{ t('quiz.questionTypes.CONNECT') }}
      </SecondaryButton>
      <SecondaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
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
          <span class="text-xs font-semibold text-(--text-muted) uppercase">1. {{ t('quiz.questionTypes.MULTIPLE_CHOICE') }}</span>
          <DeleteButton disabled />
        </div>
        <TextInput model-value="Was ist kein Loeschmittel?" disabled />
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
          <span class="text-xs text-(--text-muted)">5 {{ t('quiz.questions') }}</span>
        </div>
        <div class="flex items-center justify-between">
          <span class="font-medium text-sm">Fortgeschritten</span>
          <span class="text-xs text-(--text-muted)">7 {{ t('quiz.questions') }}</span>
        </div>
      </div>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.quiz.catalogDetailTip') }}</HelpTip>
  </HelpArticle>
</template>
