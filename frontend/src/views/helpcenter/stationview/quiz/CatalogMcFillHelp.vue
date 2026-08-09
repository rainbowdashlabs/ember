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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleSwitch from '@/components/input/toggle/ToggleSwitch.vue'

const {t} = useI18n()

const CORRECT = ['Sauerstoff']
const EXISTING_WRONG = ['Stickstoff']
const SUGGESTED = ['Kohlendioxid', 'Wasserstoff']

const noop = () => undefined
</script>

<template>
  <HelpArticle :title="t('helpCenter.quizCatalogMcFill.title')"
               :subtitle="t('helpCenter.quizCatalogMcFill.subtitle')">
    <HelpSection :title="t('helpCenter.quizCatalogMcFill.whatIs')">
      <p>{{ t('helpCenter.quizCatalogMcFill.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizCatalogMcFill.howTo')">
      <p>{{ t('helpCenter.quizCatalogMcFill.howToStep1') }}</p>
      <p>{{ t('helpCenter.quizCatalogMcFill.howToStep2') }}</p>
      <p>{{ t('helpCenter.quizCatalogMcFill.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizCatalogMcFill.exampleTitle')">
      <p>{{ t('helpCenter.quizCatalogMcFill.exampleText') }}</p>
      <div class="flex items-center gap-2 mb-4">
        <SecondaryButton :icon="['fas', 'chevron-left']">Grundlagen Atemschutz</SecondaryButton>
        <SectionHeader>{{ t('quiz.ai.fillMcAnswers') }}</SectionHeader>
      </div>
      <NeutralContainer class="space-y-4 max-w-md">
        <MutedText tag="p">{{ t('quiz.ai.fillMcHint') }}</MutedText>
        <div class="flex items-center gap-3">
          <ToggleSwitch model-value="fillTo" option-a="add" option-b="fillTo"
                        :label-a="t('quiz.ai.modeAdd')" :label-b="t('quiz.ai.modeFillTo')"/>
          <NumberInput :model-value="6" class="w-16"/>
        </div>
        <PrimaryButton :icon="['fas', 'brain']">{{ t('quiz.ai.generate') }}</PrimaryButton>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizCatalogMcFill.reviewTitle')">
      <p>{{ t('helpCenter.quizCatalogMcFill.reviewText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizCatalogMcFill.reviewExampleTitle')">
      <div class="flex items-center justify-between mb-4 flex-wrap gap-2">
        <MutedText>
          1 {{ t('quiz.ai.questionsWithNewAnswers') }} · 2 {{ t('quiz.ai.newAnswersTotal') }}
        </MutedText>
        <div class="flex gap-2">
          <SecondaryButton>{{ t('common.back') }}</SecondaryButton>
          <SaveButton :action="noop"/>
        </div>
      </div>
      <NeutralContainer>
        <div class="flex items-start justify-between gap-2 mb-3">
          <SubHeader>Welches Gas atmen wir ein?</SubHeader>
          <MutedIconButton :icon="['fas', 'xmark']" :label="t('common.remove')" hover="error" class="shrink-0"/>
        </div>
        <div class="space-y-1.5">
          <div v-for="opt in CORRECT" :key="opt"
               class="flex items-center gap-2 px-3 py-1.5 rounded bg-success/10 border border-success/30">
            <font-awesome-icon :icon="['fas', 'check']" class="text-success text-xs shrink-0"/>
            <span class="text-sm">{{ opt }}</span>
          </div>
          <div v-for="opt in EXISTING_WRONG" :key="opt"
               class="flex items-center gap-2 px-3 py-1.5 rounded bg-(--bg-accent)">
            <MutedIcon :icon="['fas', 'xmark']" size="inline" class="shrink-0"/>
            <span class="text-sm text-(--text-muted)">{{ opt }}</span>
          </div>
          <div v-for="answer in SUGGESTED" :key="answer"
               class="flex items-center gap-2 rounded border-2 border-primary/40 bg-primary/5">
            <font-awesome-icon :icon="['fas', 'star']" class="text-primary text-xs shrink-0 ml-3"/>
            <TextInput :model-value="answer" class="flex-1 !border-0 !bg-transparent !ring-0 !shadow-none"/>
            <DeleteButton class="mr-1"/>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizCatalogMcFill.saveTitle')">
      <p>{{ t('helpCenter.quizCatalogMcFill.saveText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizCatalogMcFill.keyTitle')">
      <p>{{ t('helpCenter.quizCatalogMcFill.keyText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quizCatalogMcFill.tip') }}</HelpTip>
  </HelpArticle>
</template>
