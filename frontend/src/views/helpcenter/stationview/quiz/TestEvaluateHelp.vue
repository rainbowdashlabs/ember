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
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quizTestEvaluate.title')" :subtitle="t('helpCenter.quizTestEvaluate.subtitle')">
    <HelpSection :title="t('helpCenter.quizTestEvaluate.whatIs')">
      <p>{{ t('helpCenter.quizTestEvaluate.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizTestEvaluate.autoTitle')">
      <p>{{ t('helpCenter.quizTestEvaluate.autoText') }}</p>
    </HelpSection>

    <NeutralContainer>
      <div class="space-y-3 text-sm">
        <div class="space-y-1">
          <div class="font-medium">{{ t('helpCenter.sample.quiz.fireNumber') }}</div>
          <div class="flex items-center gap-2 text-xs">
            <span class="text-[var(--text-muted)]">{{ t('helpCenter.sample.quiz.answer') }}</span>
            <span>112</span>
            <SuccessBadge class="ml-auto">{{ t('helpCenter.sample.protocol.pointsOfSpaced', {points: 2, total: 2}) }}</SuccessBadge>
          </div>
        </div>
        <div class="space-y-1">
          <div class="font-medium">{{ t('helpCenter.sample.quiz.explainHoses') }}</div>
          <div class="flex items-center gap-2 text-xs">
            <span class="text-[var(--text-muted)]">{{ t('helpCenter.sample.quiz.answer') }}</span>
            <span class="italic">{{ t('helpCenter.sample.quiz.hosesAnswer') }}</span>
          </div>
          <div class="flex justify-end">
            <InfoBadge>{{ t('helpCenter.quizTestEvaluate.manualLabel') }}</InfoBadge>
          </div>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.quizTestEvaluate.manualTitle')">
      <p>{{ t('helpCenter.quizTestEvaluate.manualText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizTestEvaluate.howTo')">
      <p>{{ t('helpCenter.quizTestEvaluate.step1') }}</p>
      <p>{{ t('helpCenter.quizTestEvaluate.step2') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quizTestEvaluate.tip') }}</HelpTip>
  </HelpArticle>
</template>
