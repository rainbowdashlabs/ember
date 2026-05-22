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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import IconButton from '@/components/button/IconButton.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quiz.testDetailTitle')" :subtitle="t('helpCenter.quiz.testDetailSubtitle')">
    <HelpSection :title="t('helpCenter.quiz.lifecycleTitle')">
      <p>{{ t('helpCenter.quiz.lifecycleText') }}</p>
      <ul class="list-disc pl-5 space-y-1 mt-2 text-sm">
        <li><strong>{{ t('quiz.statusDraft') }}:</strong> {{ t('helpCenter.quiz.statusDraftDesc') }}</li>
        <li><strong>{{ t('quiz.statusActive') }}:</strong> {{ t('helpCenter.quiz.statusActiveDesc') }}</li>
        <li><strong>{{ t('quiz.statusClosed') }}:</strong> {{ t('helpCenter.quiz.statusClosedDesc') }}</li>
      </ul>
    </HelpSection>

    <!-- Dummy: Lifecycle flow -->
    <NeutralContainer>
      <div class="flex items-center justify-center gap-3 flex-wrap">
        <InfoBadge>{{ t('quiz.statusDraft') }}</InfoBadge>
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)" />
        <SuccessBadge>{{ t('quiz.statusActive') }}</SuccessBadge>
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)" />
        <ErrorBadge>{{ t('quiz.statusClosed') }}</ErrorBadge>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.quiz.sectionsTitle')">
      <p>{{ t('helpCenter.quiz.sectionsText') }}</p>
    </HelpSection>

    <!-- Dummy: Section -->
    <NeutralContainer>
      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <span class="font-medium text-sm">Abschnitt 1: Grundwissen</span>
          <span class="text-xs text-(--text-muted)">5 {{ t('quiz.questions') }}</span>
        </div>
        <p class="text-xs text-(--text-muted)">Quelle: Brandschutz Grundlagen (Kategorie: Grundwissen)</p>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.quiz.frozenQuestionsTitle')">
      <p>{{ t('helpCenter.quiz.frozenQuestionsText') }}</p>
      <p class="mt-2">{{ t('helpCenter.quiz.frozenQuestionsSwapText') }}</p>
      <p class="mt-2">{{ t('helpCenter.quiz.frozenQuestionsActivateText') }}</p>
    </HelpSection>

    <!-- Dummy: Frozen questions with swap buttons -->
    <NeutralContainer>
      <div class="space-y-3">
        <div class="flex items-center justify-between mb-2">
          <span class="font-semibold text-sm">Testfragen (3)</span>
          <SecondaryButton disabled>
            <font-awesome-icon :icon="['fas', 'rotate']" class="mr-1" />
            Fragen generieren
          </SecondaryButton>
        </div>
        <NeutralContainer>
          <div class="flex items-start gap-3">
            <span class="text-xs text-(--text-muted) w-6 shrink-0 pt-0.5">1.</span>
            <div class="flex-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="text-sm font-medium">Was ist die Hauptaufgabe der Feuerwehr?</span>
                <SecondaryBadge>Multiple Choice</SecondaryBadge>
                <InfoBadge>2 Punkte</InfoBadge>
              </div>
            </div>
            <div class="flex gap-1 shrink-0">
              <IconButton :icon="['fas', 'shuffle']" label="Zufällig" class="text-(--text-muted)" />
              <IconButton :icon="['fas', 'arrow-right-arrow-left']" label="Auswählen" class="text-(--text-muted)" />
            </div>
          </div>
        </NeutralContainer>
        <NeutralContainer>
          <div class="flex items-start gap-3">
            <span class="text-xs text-(--text-muted) w-6 shrink-0 pt-0.5">2.</span>
            <div class="flex-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="text-sm font-medium">Ordne die Schläuche dem Durchmesser zu.</span>
                <SecondaryBadge>Zuordnung</SecondaryBadge>
                <InfoBadge>4 Punkte</InfoBadge>
              </div>
            </div>
            <div class="flex gap-1 shrink-0">
              <IconButton :icon="['fas', 'shuffle']" label="Zufällig" class="text-(--text-muted)" />
              <IconButton :icon="['fas', 'arrow-right-arrow-left']" label="Auswählen" class="text-(--text-muted)" />
            </div>
          </div>
        </NeutralContainer>
        <NeutralContainer>
          <div class="flex items-start gap-3">
            <span class="text-xs text-(--text-muted) w-6 shrink-0 pt-0.5">3.</span>
            <div class="flex-1">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="text-sm font-medium">Die Feuerwehr ist nur für Brände zuständig.</span>
                <SecondaryBadge>Wahr/Falsch</SecondaryBadge>
                <InfoBadge>1 Punkt</InfoBadge>
              </div>
            </div>
            <div class="flex gap-1 shrink-0">
              <IconButton :icon="['fas', 'shuffle']" label="Zufällig" class="text-(--text-muted)" />
              <IconButton :icon="['fas', 'arrow-right-arrow-left']" label="Auswählen" class="text-(--text-muted)" />
            </div>
          </div>
        </NeutralContainer>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.quiz.gradingTitle')">
      <p>{{ t('helpCenter.quiz.gradingText') }}</p>
    </HelpSection>

    <!-- Dummy: Grading -->
    <NeutralContainer>
      <div class="space-y-3">
        <div class="flex items-center justify-between">
          <span class="font-medium text-sm">Max Mustermann</span>
          <div class="flex items-center gap-2">
            <span class="text-sm">8 / 10 {{ t('quiz.points') }}</span>
            <SecondaryButton disabled>{{ t('quiz.evaluate.title') }}</SecondaryButton>
          </div>
        </div>
        <div class="flex items-center justify-between">
          <span class="font-medium text-sm">Lisa Beispiel</span>
          <div class="flex items-center gap-2">
            <InfoBadge>{{ t('quiz.statusSubmitted') }}</InfoBadge>
            <PrimaryButton disabled>{{ t('quiz.evaluate.title') }}</PrimaryButton>
          </div>
        </div>
      </div>
    </NeutralContainer>

    <!-- Dummy: Grading detail -->
    <NeutralContainer>
      <div class="space-y-3">
        <span class="text-xs font-semibold text-(--text-muted) uppercase">{{ t('quiz.evaluate.title') }}</span>
        <div>
          <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.evaluate.studentAnswer') }}</label>
          <p class="text-sm px-3 py-2 rounded border border-bg-light-accent dark:border-bg-dark-accent">Wasser</p>
        </div>
        <div>
          <label class="text-xs text-success block mb-1">{{ t('quiz.evaluate.correctAnswer') }}</label>
          <p class="text-sm px-3 py-2 rounded border border-success/30 bg-success/10">Wasser, Schaum</p>
        </div>
        <div class="flex items-center gap-3">
          <label class="text-sm font-medium">{{ t('quiz.evaluate.points') }}</label>
          <NumberInput :model-value="1" disabled class="w-24" />
          <span class="text-sm text-(--text-muted)">/ 2</span>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.quiz.accessTitle')">
      <p>{{ t('helpCenter.quiz.accessText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quiz.testDetailTip') }}</HelpTip>
  </HelpArticle>
</template>
